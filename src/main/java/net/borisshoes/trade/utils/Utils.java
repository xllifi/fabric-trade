package net.borisshoes.trade.utils;

import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class Utils {
   public static MutableText simpleColoredText(String text, Formatting... formatting) {
      return Text.literal(text).setStyle(formatting != null ? Style.EMPTY.withFormatting(formatting) : Style.EMPTY);
   }
   
   public static void playSongToPlayer(ServerPlayerEntity player, RegistryEntry<SoundEvent> event, float vol, float pitch) {
      player.networkHandler.sendPacket(new PlaySoundS2CPacket(event, SoundCategory.PLAYERS, player.getEntityPos().x,player.getEntityPos().y, player.getEntityPos().z, vol, pitch, 0));
   }
}
