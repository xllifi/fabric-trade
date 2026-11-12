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
   public static MutableComponent simpleColoredText(String text, ChatFormatting... formatting) {
      return Component.literal(text).setStyle(formatting != null ? Style.EMPTY.applyFormats(formatting) : Style.EMPTY);
   }
   
   public static void playSongToPlayer(ServerPlayer player, Holder<SoundEvent> event, float vol, float pitch) {
      player.connection.send(new ClientboundSoundPacket(event, SoundSource.PLAYERS, player.position().x,player.position().y, player.position().z, vol, pitch, 0));
   }
}
