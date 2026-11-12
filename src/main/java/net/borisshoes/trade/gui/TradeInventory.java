package net.borisshoes.trade.gui;

import net.minecraft.world.SimpleContainer;

public class TradeInventory extends SimpleContainer {
   private TradeInventoryListener listener;

   public TradeInventory(){
      super(24);
   }

   public void addListener(TradeInventoryListener listener) {
      this.listener = listener;
   }

   @Override
   public void setChanged() {
      super.setChanged();
      if (listener != null) {
         listener.containerChanged(this);
      }
   }
}
