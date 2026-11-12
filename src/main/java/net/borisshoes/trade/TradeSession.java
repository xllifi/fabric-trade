package net.borisshoes.trade;

import com.mojang.authlib.GameProfile;
import eu.pb4.sgui.api.SguiUtils;
import eu.pb4.sgui.api.elements.GuiElementBuilder;
import eu.pb4.sgui.api.gui.GuiLike;
import net.borisshoes.trade.gui.TradeGui;
import net.borisshoes.trade.gui.TradeInventory;
import net.borisshoes.trade.gui.TradeInventoryListener;
import net.borisshoes.trade.utils.Utils;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.Holder;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.util.Prediction;

import java.util.Timer;
import java.util.TimerTask;

public class TradeSession {
   private final ServerPlayer tFrom,tTo;
   private final Trade.TradeRequest tr;
   private final TradeGui guiFrom,guiTo;
   private final TradeInventory inv;
   private final TradeInventoryListener listener;
   private final int[] yourSlots = {10,11,12,19,20,21,28,29,30,37,38,39};
   private final int[] theirSlots = {14,15,16,23,24,25,32,33,34,41,42,43};
   private final int[] borderSlots = {0,1,3,4,5,7,8,45,46,47,48,50,51,52,53,18,27,36,26,35,44};
   private final int[] middleSlots = {13,22,31,40};
   private boolean closing = false;
   private boolean fromReady = false;
   private boolean toReady = false;
   private final ItemStack[] readyState;
   private final Trade main;
   
   public TradeSession(ServerPlayer tFrom, ServerPlayer tTo, Trade.TradeRequest tr, Trade main){
      this.tFrom = tFrom;
      this.tTo = tTo;
      this.tr = tr;
      this.main = main;
      
      inv = new TradeInventory();
      listener = new TradeInventoryListener(this);
      inv.addListener(listener);
      
      readyState = new ItemStack[24];
      for(int i = 0; i<24;i++){readyState[i]=ItemStack.EMPTY;}
   
      guiFrom = new TradeGui(MenuType.GENERIC_9x6,tFrom,this);
      guiTo = new TradeGui(MenuType.GENERIC_9x6,tTo,this);
      guiFrom.setTitle(Component.translatable("ui.fabrictrade.title", tTo.getName().getString()));
      guiTo.setTitle(Component.translatable("ui.fabrictrade.title", tFrom.getName().getString()));
      
      for(int i=0; i<12;i++){
         guiFrom.setSlot(yourSlots[i], new Slot(inv,i,0,0));
         guiTo.setSlot(yourSlots[i], new Slot(inv,i+12,0,0));
      }
   
      buildBorder();
      guiTo.setSlot(9,new GuiElementBuilder(Items.CONCRETE.red()).setName(Component.translatable("ui.fabrictrade.confirm")));
      guiFrom.setSlot(9,new GuiElementBuilder(Items.CONCRETE.red()).setName(Component.translatable("ui.fabrictrade.confirm")));
      guiTo.setSlot(17,new GuiElementBuilder(Items.CONCRETE.red()).setName(Component.translatable("ui.fabrictrade.waiting")));
      guiFrom.setSlot(17,new GuiElementBuilder(Items.CONCRETE.red()).setName(Component.translatable("ui.fabrictrade.waiting")));
      fromReady = false;
      toReady = false;
      
      GuiLike curToGui = SguiUtils.getCurrentGui(tTo);
      if(curToGui != null){
         curToGui.close();
      }
      GuiLike curFromGui = SguiUtils.getCurrentGui(tFrom);
      if(curFromGui != null){
         curFromGui.close();
      }
      
      guiFrom.open();
      guiTo.open();
   
      Utils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_GUITAR,1f,.5f);
      Utils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_GUITAR,1f,.5f);
      Timer timer = new Timer();
      timer.schedule(new TimerTask() {
         @Override
         public void run() {
            Utils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_GUITAR,1f,1f);
            Utils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_GUITAR,1f,1f);
         }
      }, 500);
   }
   
   public void buildBorder(){
      for(int i=0;i<borderSlots.length;i++){
         guiFrom.setSlot(borderSlots[i],new GuiElementBuilder(Items.STAINED_GLASS_PANE.black()).hideTooltip());
         guiTo.setSlot(borderSlots[i],new GuiElementBuilder(Items.STAINED_GLASS_PANE.black()).hideTooltip());
      }
      for(int i=0;i<middleSlots.length;i++){
         guiFrom.setSlot(middleSlots[i],new GuiElementBuilder(Items.IRON_BARS).hideTooltip());
         guiTo.setSlot(middleSlots[i],new GuiElementBuilder(Items.IRON_BARS).hideTooltip());
      }
      
      GameProfile fromProfile = tFrom.getGameProfile();
      GuiElementBuilder fromHead1 = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(fromProfile);
      GuiElementBuilder fromHead2 = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(fromProfile);
      guiFrom.setSlot(2,fromHead1.setName((Component.translatable("ui.fabrictrade.items.your"))));
      guiTo.setSlot(6,fromHead2.setName((Component.translatable("ui.fabrictrade.items.partner", tFrom.getDisplayName()))));

      GameProfile toProfile = tTo.getGameProfile();
      GuiElementBuilder toHead1 = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(toProfile);
      GuiElementBuilder toHead2 = new GuiElementBuilder(Items.PLAYER_HEAD).setProfile(toProfile);
      guiTo.setSlot(2,toHead1.setName((Component.translatable("ui.fabrictrade.items.your"))));
      guiFrom.setSlot(6,toHead2.setName((Component.translatable("ui.fabrictrade.items.partner", tTo.getDisplayName()))));

      guiTo.setSlot(49,new GuiElementBuilder(Items.BARRIER).setName(Component.translatable("ui.fabrictrade.cancel")));
      guiFrom.setSlot(49,new GuiElementBuilder(Items.BARRIER).setName(Component.translatable("ui.fabrictrade.cancel")));
   }
   
   public void updateGuis(){
      for(int i=0;i<12;i++){
         guiFrom.setSlot(theirSlots[i],inv.getItem(i+12));
         guiTo.setSlot(theirSlots[i],inv.getItem(i));
      }
      
      listener.finishUpdate();
   }
   
   public void checkReadyStatus(){
      checkReadyStatus(tFrom);
      checkReadyStatus(tTo);
   }
   
   public void checkReadyStatus(ServerPlayer player){
      if(player.equals(tFrom)&&(fromReady||toReady)){
         //System.out.println("Running check for: "+player.getEntityName());
         //Check first 12 slots
         for(int i=0;i<12;i++){
            if(!readyState[i].equals(inv.getItem(i))){
               //System.out.println("Found Discrepancy");
               setUnready();
               return;
            }
         }
      }else if(player.equals(tTo)&&(fromReady||toReady)){
         //System.out.println("Running check for: "+player.getEntityName());
         //Check to ready status
         for(int i=12;i<24;i++){
            if(!readyState[i].equals(inv.getItem(i))){
               //System.out.println("Found Discrepancy");
               setUnready();
               return;
            }
         }
      }
   }
   
   public void setUnready(){
      guiTo.setSlot(9,new GuiElementBuilder(Items.CONCRETE.red()).setName(Component.translatable("ui.fabrictrade.confirm")));
      guiFrom.setSlot(9,new GuiElementBuilder(Items.CONCRETE.red()).setName(Component.translatable("ui.fabrictrade.confirm")));
      guiTo.setSlot(17,new GuiElementBuilder(Items.CONCRETE.red()).setName(Component.translatable("ui.fabrictrade.waiting")));
      guiFrom.setSlot(17,new GuiElementBuilder(Items.CONCRETE.red()).setName(Component.translatable("ui.fabrictrade.waiting")));
      fromReady = false;
      toReady = false;
      Utils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_HAT,1f,.5f);
      Utils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_HAT,1f,.5f);
   }
   
   public void setReady(ServerPlayer player){
      if(player.equals(tFrom)&&!fromReady){
         guiFrom.setSlot(9,new GuiElementBuilder(Items.CONCRETE.green()).setName(Component.translatable("ui.fabrictrade.confirmed.you")));
         guiTo.setSlot(17,new GuiElementBuilder(Items.CONCRETE.green()).setName(Component.translatable("ui.fabrictrade.confirmed.partner")));
         fromReady = true;
         Utils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_PLING,1f,1f);
         Utils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_PLING,1f,2f);
         for(int i=0;i<12;i++){
            readyState[i] = inv.getItem(i);
         }
      }else if(player.equals(tTo)&&!toReady){
         guiTo.setSlot(9,new GuiElementBuilder(Items.CONCRETE.green()).setName(Component.translatable("ui.fabrictrade.confirmed.you")));
         guiFrom.setSlot(17,new GuiElementBuilder(Items.CONCRETE.green()).setName(Component.translatable("ui.fabrictrade.confirmed.partner")));
         toReady = true;
         Utils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_PLING,1f,2f);
         Utils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_PLING,1f,1f);
         for(int i=12;i<24;i++){
            readyState[i] = inv.getItem(i);
         }
      }
      
      if(fromReady&&toReady){
         finalizeTrade();
         
         Timer timer = new Timer();
         timer.schedule(new TimerTask() {
            @Override
            public void run() {
               Utils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_BELL,1.5f,1f);
               Utils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_BELL,1.5f,1f);
               Timer timer2 = new Timer();
               timer2.schedule(new TimerTask() {
                  @Override
                  public void run() {
                     Utils.playSongToPlayer(tTo, SoundEvents.NOTE_BLOCK_BELL,1.5f,2f);
                     Utils.playSongToPlayer(tFrom, SoundEvents.NOTE_BLOCK_BELL,1.5f,2f);
                  }
               }, 500);
            }
         }, 500);
      }
   }
   
   public void finalizeTrade(){
      //Give Items
      for(int i=0; i<24;i++){
         ItemStack stack = inv.getItem(i);
         if(!stack.isEmpty()){
            ServerPlayer player;
            if(i<12){
               player = tTo;
            }else{
               player = tFrom;
            }
   
            ItemEntity itemEntity;
            boolean bl = player.getInventory().add(stack);
            if (!bl || !stack.isEmpty()) {
               itemEntity = player.drop(stack, false, Prediction.SERVER_ONLY);
               if (itemEntity == null) continue;
               itemEntity.setNoPickUpDelay();
               itemEntity.setTarget(player.getUUID());
               continue;
            }
            stack.setCount(1);
            itemEntity = player.drop(stack, false, Prediction.SERVER_ONLY);
            if (itemEntity != null) {
               itemEntity.makeFakeItem();
            }
         }
      }
      
      main.sendTradeMessage(tFrom, Component.translatable("messages.fabrictrade.request.completed"));
      main.sendTradeMessage(tTo, Component.translatable("messages.fabrictrade.request.completed"));
      closing = true;
      guiFrom.close();
      guiTo.close();
      
      main.completeSession(tFrom,tTo,tr);
   }
   
   public void cancelTrade(ServerPlayer player){
      if(!closing){
         closing = true;
   
         //Give Items back
         for(int i=0; i<24;i++){
            ItemStack stack = inv.getItem(i);
            if(!stack.isEmpty()){
               ServerPlayer player1;
               if(i>=12){
                  player1 = tTo;
               }else{
                  player1 = tFrom;
               }
               
               if(player1.hasDisconnected()){
                  player1.drop(stack, false, Prediction.SERVER_ONLY);
               }else{
                  ItemEntity itemEntity;
                  boolean bl = player1.getInventory().add(stack);
                  if (!bl || !stack.isEmpty()) {
                     itemEntity = player1.drop(stack, false, Prediction.SERVER_ONLY);
                     if (itemEntity == null) continue;
                     itemEntity.setNoPickUpDelay();
                     itemEntity.setTarget(player1.getUUID());
                     continue;
                  }
                  stack.setCount(1);
                  itemEntity = player1.drop(stack, false, Prediction.SERVER_ONLY);
                  if (itemEntity != null) {
                     itemEntity.makeFakeItem();
                  }
               }
            }
         }
         
         main.sendTradeMessage(player, Component.translatable("messages.fabrictrade.request.cancel.you").withStyle(ChatFormatting.RED));
         ServerPlayer other = player.equals(tFrom) ? tTo : tFrom;
         main.sendTradeMessage(other, Component.translatable("messages.fabrictrade.request.cancel.partner", player.getDisplayName()));
   
         guiFrom.close();
         guiTo.close();
         
         
         Utils.playSongToPlayer(tFrom, Holder.direct(SoundEvents.FIRE_EXTINGUISH) ,1f,.5f);
         Utils.playSongToPlayer(tTo, Holder.direct(SoundEvents.FIRE_EXTINGUISH),1f,.5f);
      }
   }
}
