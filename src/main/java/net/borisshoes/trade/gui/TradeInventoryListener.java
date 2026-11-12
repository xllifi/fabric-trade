package net.borisshoes.trade.gui;

import net.borisshoes.trade.TradeSession;
import net.minecraft.world.Container;

public class TradeInventoryListener {
   
   TradeSession session;
   private boolean updating = false;
   
   public TradeInventoryListener(TradeSession session){
      this.session = session;
   }
   
   public void containerChanged(Container inv){
      if(!updating){
         updating = true;
         session.checkReadyStatus();
         session.updateGuis();
         
      }
   }
   
   public void finishUpdate(){
      updating = false;
   }
}
