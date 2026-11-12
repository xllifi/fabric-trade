package net.borisshoes.trade;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.borisshoes.trade.utils.ConfigUtils;
import net.borisshoes.trade.utils.Utils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.*;
import net.minecraft.ChatFormatting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.minecraft.commands.arguments.EntityArgument.getPlayer;
import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class Trade implements ModInitializer {
  private static final Logger logger = LogManager.getLogger("Trade");
  private static final String CONFIG_NAME = "Trade.properties";

  private final ArrayList<TradeRequest> activeTrades = new ArrayList<>();
  private final HashMap<UUID, Long> recentRequests = new HashMap<>();
  private ConfigUtils config;

  @Nullable
  private static CompletableFuture<Suggestions> filterSuggestionsByInput(SuggestionsBuilder builder, List<String> values) {
    String start = builder.getRemaining().toLowerCase();
    values.stream().filter(s -> s.toLowerCase().startsWith(start)).forEach(builder::suggest);
    return builder.buildFuture();
  }

  private CompletableFuture<Suggestions> getTradeInitSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    CommandSourceStack scs = context.getSource();

    List<String> activeTargets = Stream.concat(
      activeTrades.stream().map(tradeRequest -> tradeRequest.tTo.getName().getString()), activeTrades
        .stream()
        .map(tradeRequest -> tradeRequest.tFrom.getName().getString())
    ).collect(Collectors.toList());
    List<String> others = Arrays
      .stream(scs.getServer().getPlayerList().getPlayerNamesArray())
      .filter(s -> !s.equals(scs.getTextName()) && !activeTargets.contains(s))
      .collect(Collectors.toList());
    return filterSuggestionsByInput(builder, others);
  }

  private CompletableFuture<Suggestions> getTradeTargetSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> activeTargets = activeTrades
      .stream()
      .map(tradeRequest -> tradeRequest.tFrom.getName().getString())
      .collect(Collectors.toList());
    return filterSuggestionsByInput(builder, activeTargets);
  }

  private CompletableFuture<Suggestions> getTradeSenderSuggestions(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
    List<String> activeTargets = activeTrades
      .stream()
      .map(tradeRequest -> tradeRequest.tTo.getName().getString())
      .collect(Collectors.toList());
    return filterSuggestionsByInput(builder, activeTargets);
  }

  static class CooldownModeConfigValue extends ConfigUtils.IConfigValue<TradeCooldownMode> {
    public CooldownModeConfigValue(@NotNull String name, TradeCooldownMode defaultValue, @Nullable ConfigUtils.Command command) {
      super(
        name, defaultValue, null, command, (context, builder) -> {
          List<String> tcmValues = Arrays
            .stream(TradeCooldownMode.values())
            .map(String::valueOf)
            .collect(Collectors.toList());
          return filterSuggestionsByInput(builder, tcmValues);
        }
      );
    }

    @Override
    public TradeCooldownMode getFromProps(Properties props) {
      return TradeCooldownMode.valueOf(props.getProperty(name));
    }

    @Override
    public ArgumentType<?> getArgumentType() {
      return StringArgumentType.string();
    }

    @Override
    public TradeCooldownMode parseArgumentValue(CommandContext<CommandSourceStack> ctx) {
      return TradeCooldownMode.valueOf(StringArgumentType.getString(ctx, name));
    }
  }

  @Override
  public void onInitialize() {
    logger.info("Initializing Trade...");

    config = new ConfigUtils(
      FabricLoader
        .getInstance()
        .getConfigDir()
        .resolve(CONFIG_NAME)
        .toFile(), logger, Arrays.asList(new ConfigUtils.IConfigValue[]{new ConfigUtils.StringConfigValue("chat-prefix", "§6[Trade] §r", null), new ConfigUtils.IntegerConfigValue("timeout", 60, new ConfigUtils.IntegerConfigValue.IntLimits(0), new ConfigUtils.Command("Timeout is %s seconds", "Timeout set to %s seconds")), new ConfigUtils.IntegerConfigValue("cooldown", 60, new ConfigUtils.IntegerConfigValue.IntLimits(0), new ConfigUtils.Command("Cooldown is %s seconds", "Cooldown set to %s seconds")), new CooldownModeConfigValue("cooldown-mode", TradeCooldownMode.WhoInitiated, new ConfigUtils.Command("Cooldown Mode is %s", "Cooldown Mode set to %s"))})
    );

    CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, registrationEnvironment) -> {
      dispatcher.register(literal("trade").then(argument("target", EntityArgument.player())
        .suggests(this::getTradeInitSuggestions)
        .executes(ctx -> tradeInit(ctx, getPlayer(ctx, "target")))));

      dispatcher.register(literal("tradeaccept")
        .then(argument("target", EntityArgument.player())
          .suggests(this::getTradeTargetSuggestions)
          .executes(ctx -> tradeAccept(ctx, getPlayer(ctx, "target"))))
        .executes(ctx -> tradeAccept(ctx, null)));

      dispatcher.register(literal("tradedeny")
        .then(argument("target", EntityArgument.player())
          .suggests(this::getTradeTargetSuggestions)
          .executes(ctx -> tradeDeny(ctx, getPlayer(ctx, "target"))))
        .executes(ctx -> tradeDeny(ctx, null)));

      dispatcher.register(literal("tradecancel")
        .then(argument("target", EntityArgument.player())
          .suggests(this::getTradeSenderSuggestions)
          .executes(ctx -> tradeCancel(ctx, getPlayer(ctx, "target"))))
        .executes(ctx -> tradeCancel(ctx, null)));

      dispatcher.register(config.generateCommand("tradeconfig"));
    });

  }

  public void sendTradeMessage(ServerPlayer player, Component message) {
    player.sendSystemMessage(Component.empty()
      .append(Utils.legacyText((String) config.getValue("chat-prefix")))
      .append(message), false);
  }

  public int tradeInit(CommandContext<CommandSourceStack> ctx, ServerPlayer tTo) throws CommandSyntaxException {
    final ServerPlayer tFrom = ctx.getSource().getPlayer();

    if (tFrom.equals(tTo)) {
      sendTradeMessage(tFrom, Component.translatable("messages.fabrictrade.request.self"));
      return 1;
    }

    if (checkCooldown(tFrom)) return 1;

    TradeRequest tr = new TradeRequest(tFrom, tTo, (int) config.getValue("timeout") * 1000);
    if (activeTrades.stream().anyMatch(tpaRequest -> tpaRequest.equals(tr))) {
      sendTradeMessage(tFrom, Component.translatable("messages.fabrictrade.request.exists"));
      return 1;
    }
    tr.setTimeoutCallback(() -> {
      activeTrades.remove(tr);
      sendTradeMessage(tFrom, Component.translatable("messages.fabrictrade.request.timeout.sender", tTo.getDisplayName()));
      sendTradeMessage(tTo, Component.translatable("messages.fabrictrade.request.timeout.receiver", tFrom.getDisplayName()));
    });
    activeTrades.add(tr);

    sendTradeMessage(tFrom,
      Component.translatable(
        "messages.fabrictrade.request.new.sender", tTo.getDisplayName(), Component
          .translatable("syntax.fabrictrade.cancel")
          .withStyle(s -> s
            .withClickEvent(new ClickEvent.RunCommand("/tradecancel " + tTo.getName().getString()))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradecancel " + tTo.getName().getString())))
            .withColor(ChatFormatting.GOLD)), config.getValue("timeout").toString()
      )
    );

    sendTradeMessage(tTo,
      Component.translatable(
        "messages.fabrictrade.request.new.receiver", tFrom.getDisplayName(), Component
          .translatable("syntax.fabrictrade.accept")
          .withStyle(s -> s
            .withClickEvent(new ClickEvent.RunCommand("/tradeaccept " + tFrom.getName().getString()))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradeaccept " + tFrom.getName().getString())))
            .withColor(ChatFormatting.GOLD)), Component
          .translatable("syntax.fabrictrade.deny")
          .withStyle(s -> s
            .withClickEvent(new ClickEvent.RunCommand("/tradedeny " + tFrom.getName().getString()))
            .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradedeny " + tFrom.getName().getString())))
            .withColor(ChatFormatting.GOLD)), config.getValue("timeout").toString()
      )
    );
    return 1;
  }

  private Optional<MutableComponent> failMessageIfMultiple(TradeRequest[] candidates, String key) {
    if (candidates.length > 1) {
      MutableComponent text = Component.translatable("messages.fabrictrade.request." + key + ".multiple");
      for (TradeRequest candidate : candidates) {
        var name = candidate.tFrom.getName().getString();
        text
          .append("\n")
          .append(Component.translatable("messages.fabrictrade.request.multiple.marker").withStyle(ChatFormatting.GRAY))
          .append(" ")
          .append(
            Component
              .translatable("messages.fabrictrade.request.multiple.requester", name)
              .withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand("/tradeaccept " + name))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("/tradeaccept " + name)))
                .withColor(ChatFormatting.GOLD)
              )
          );
      }
      return Optional.of(text);
    }
    return Optional.empty();
  }

  public int tradeAccept(CommandContext<CommandSourceStack> ctx, ServerPlayer tFrom) throws CommandSyntaxException {
    final ServerPlayer tTo = ctx.getSource().getPlayer();

    if (tFrom == null) {
      TradeRequest[] candidates;
      candidates = activeTrades.stream().filter(tpaRequest -> tpaRequest.tTo.equals(tTo)).toArray(TradeRequest[]::new);
      var text = failMessageIfMultiple(candidates, "accept");
      if (text.isPresent()) {
        sendTradeMessage(tTo, text.get());
        return 1;
      }
      if (candidates.length < 1) {
        sendTradeMessage(tTo, Component.translatable("messages.fabrictrade.request.none"));
        return 1;
      }
      tFrom = candidates[0].tFrom;
    }

    TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.ACCEPT);
    if (tr == null) return 1;

    // Do the trade thing
    new TradeSession(tFrom, tTo, tr, this);

    tr.cancelTimeout();
    activeTrades.remove(tr);
    sendTradeMessage(tr.tTo, Component.translatable("messages.fabrictrade.request.accept.you"));
    sendTradeMessage(tr.tFrom, Component.translatable("messages.fabrictrade.request.accept.partner", tr.tTo.getDisplayName()));
    return 1;
  }


  public int tradeDeny(CommandContext<CommandSourceStack> ctx, ServerPlayer tFrom) throws CommandSyntaxException {
    final ServerPlayer tTo = ctx.getSource().getPlayer();

    if (tFrom == null) {
      TradeRequest[] candidates;
      candidates = activeTrades.stream().filter(tpaRequest -> tpaRequest.tTo.equals(tTo)).toArray(TradeRequest[]::new);
      var text = failMessageIfMultiple(candidates, "deny");
      if (text.isPresent()) {
        sendTradeMessage(tTo, text.get());
        return 1;
      }
      if (candidates.length < 1) {
        sendTradeMessage(tTo, Component.translatable("messages.fabrictrade.request.none"));
        return 1;
      }
      tFrom = candidates[0].tFrom;
    }

    TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.DENY);
    if (tr == null) return 1;
    tr.cancelTimeout();
    activeTrades.remove(tr);
    sendTradeMessage(tr.tTo, Component.translatable("messages.fabrictrade.request.deny.you"));
    sendTradeMessage(tr.tFrom, Component.translatable("messages.fabrictrade.request.deny.partner", tr.tTo.getDisplayName()));
    return 1;
  }

  public int tradeCancel(CommandContext<CommandSourceStack> ctx, ServerPlayer tTo) throws CommandSyntaxException {
    final ServerPlayer tFrom = ctx.getSource().getPlayer();

    if (tTo == null) {
      TradeRequest[] candidates;
      candidates = activeTrades
        .stream()
        .filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom))
        .toArray(TradeRequest[]::new);
      var text = failMessageIfMultiple(candidates, "cancel");
      if (text.isPresent()) {
        sendTradeMessage(tTo, text.get());
        return 1;
      }
      if (candidates.length < 1) {
        sendTradeMessage(tFrom, Component.translatable("messages.fabrictrade.request.none"));
        return 1;
      }
      tTo = candidates[0].tTo;
    }

    TradeRequest tr = getTradeRequest(tFrom, tTo, TradeAction.CANCEL);
    if (tr == null) return 1;
    tr.cancelTimeout();
    activeTrades.remove(tr);
    sendTradeMessage(tr.tTo, Component.translatable("messages.fabrictrade.request.cancel.partner", tr.tFrom.getDisplayName()));
    sendTradeMessage(tr.tFrom, Component.translatable("messages.fabrictrade.request.cancel.you"));
    return 1;
  }

  private TradeRequest getTradeRequest(ServerPlayer tFrom, ServerPlayer tTo, TradeAction action) {
    Optional<TradeRequest> otr = activeTrades
      .stream()
      .filter(tpaRequest -> tpaRequest.tFrom.equals(tFrom) && tpaRequest.tTo.equals(tTo))
      .findFirst();

    if (otr.isEmpty()) {
      if (action == TradeAction.CANCEL) {
        sendTradeMessage(tFrom, Component.translatable("messages.fabrictrade.request.none.specific"));
      } else {
        sendTradeMessage(tTo, Component.translatable("messages.fabrictrade.request.none.specific"));
      }
      return null;
    }

    return otr.get();
  }


  private boolean checkCooldown(ServerPlayer tFrom) {
    if (recentRequests.containsKey(tFrom.getUUID())) {
      long diff = Instant.now().getEpochSecond() - recentRequests.get(tFrom.getUUID());
      if (diff < (int) config.getValue("cooldown")) {
        sendTradeMessage(tFrom, Component.translatable("messages.fabrictrade.request.cooldown"));
        return true;
      }
    }
    return false;
  }

  private enum TradeAction {
    ACCEPT, DENY, CANCEL
  }

  public static class TradeRequest {
    ServerPlayer tFrom;
    ServerPlayer tTo;

    long timeout;

    Timer timer;

    public TradeRequest(ServerPlayer tFrom, ServerPlayer tTo, int timeoutMS) {
      this.tFrom = tFrom;
      this.tTo = tTo;
      this.timeout = timeoutMS;
    }

    void setTimeoutCallback(Timeout callback) {
      timer = new Timer();
      timer.schedule(
        new TimerTask() {
          @Override
          public void run() {
            callback.onTimeout();
          }
        }, timeout
      );
    }

    void cancelTimeout() {
      timer.cancel();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      TradeRequest that = (TradeRequest) o;
      return tFrom.equals(that.tFrom) && tTo.equals(that.tTo);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tFrom, tTo);
    }

    @Override
    public String toString() {
      return "TradeRequest{" + "tFrom=" + tFrom + ", tTo=" + tTo + '}';
    }
  }

  enum TradeCooldownMode {
    WhoInitiated, BothUsers
  }

  interface Timeout {
    void onTimeout();
  }

  public void completeSession(ServerPlayer tFrom, ServerPlayer tTo, TradeRequest tr) {
    switch ((TradeCooldownMode) config.getValue("cooldown-mode")) {
      case BothUsers -> {
        recentRequests.put(tr.tFrom.getUUID(), Instant.now().getEpochSecond());
        recentRequests.put(tr.tTo.getUUID(), Instant.now().getEpochSecond());
      }
      case WhoInitiated -> recentRequests.put(tr.tFrom.getUUID(), Instant.now().getEpochSecond());
    }
  }
}
