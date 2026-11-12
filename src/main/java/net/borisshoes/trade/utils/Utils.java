package net.borisshoes.trade.utils;

import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

public class Utils {
   public static MutableComponent legacyText(String text) {
      MutableComponent result = Component.empty();
      Style style = Style.EMPTY;
      int start = 0;
      for (int i = 0; i < text.length() - 1; i++) {
         if (text.charAt(i) != '§') continue;
         ChatFormatting formatting = ChatFormatting.getByCode(text.charAt(i + 1));
         if (formatting == null) continue;
         if (start < i) result.append(Component.literal(text.substring(start, i)).setStyle(style));
         if (formatting == ChatFormatting.RESET) {
            style = Style.EMPTY;
         } else {
            if (Character.digit(text.charAt(i + 1), 16) >= 0) style = Style.EMPTY;
            style = style.applyFormat(formatting);
         }
         start = ++i + 1;
      }
      if (start < text.length()) result.append(Component.literal(text.substring(start)).setStyle(style));
      return result;
   }

   public static MutableComponent simpleColoredText(String text, ChatFormatting... formatting) {
      return Component.literal(text).setStyle(formatting != null ? Style.EMPTY.applyFormats(formatting) : Style.EMPTY);
   }
   
   public static void playSongToPlayer(ServerPlayer player, Holder<SoundEvent> event, float vol, float pitch) {
      player.connection.send(new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x,player.position().y, player.position().z, vol, pitch, 0));
   }
}
