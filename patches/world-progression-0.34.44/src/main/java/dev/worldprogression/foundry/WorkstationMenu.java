package dev.worldprogression.foundry;

import net.minecraft.entity.player.*;
import net.minecraft.inventory.*;
import net.minecraft.item.*;
import net.minecraft.registry.*;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Real server-owned slots backed directly by the physical workstation. */
public final class WorkstationMenu extends ScreenHandler {
 public static final ScreenHandlerType<WorkstationMenu> TYPE=Registry.register(Registries.SCREEN_HANDLER,FoundryContent.id("workstation"),new ScreenHandlerType<>(WorkstationMenu::new,FeatureFlags.VANILLA_FEATURES));
 private final WorkstationEntity station;private final PropertyDelegate state;
 public WorkstationMenu(int sync,PlayerInventory player){this(sync,player,null,new SimpleInventory(5),new ArrayPropertyDelegate(6));}
 private WorkstationMenu(int sync,PlayerInventory player,WorkstationEntity station,Inventory storage,PropertyDelegate state){
  super(TYPE,sync);this.station=station;this.state=state;addProperties(state);
  int[] xs={20,62,104,146,232};
  for(int i=0;i<5;i++){final int slot=i;addSlot(new Slot(storage,i,xs[i],65){
   public boolean canInsert(ItemStack stack){return slot<4&&(station==null||!station.working()&&!station.demonstration&&accepts(slot,stack));}
   public boolean canTakeItems(PlayerEntity p){return station==null||!station.working()&&!station.demonstration;}
  });}
  for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,col+row*9+9,62+col*18,135+row*18));
  for(int col=0;col<9;col++)addSlot(new Slot(player,col,62+col*18,193));
 }
 private boolean accepts(int slot,ItemStack stack){
  if(slot==0)return station.acceptsInput(stack);
  if(station.kind()==FoundryContent.Station.FURNACE)return slot==1&&(stack.isOf(Items.COAL)||stack.isOf(Items.CHARCOAL)||stack.isOf(FoundryContent.item("pressed_resin")));
  if(station.kind()==FoundryContent.Station.ANVIL){
   if(slot==1&&stack.isOf(FoundryContent.item("sword_mould"))||slot==2&&stack.isOf(Items.STICK))return station.input.isEmpty()||!station.armorInput();
  }
  if(station.getWorld()==null)return false;
  return station.getWorld().getRecipeManager().listAllOfType(FoundryRecipe.TYPE).stream().anyMatch(r->r.value().station()==station.kind()&&(station.input.isEmpty()||r.value().input().ingredient().test(station.input))&&r.value().costs().get(slot).count()>0&&r.value().costs().get(slot).ingredient().test(stack));
 }
 public FoundryContent.Station kind(){return FoundryContent.Station.values()[Math.clamp(state.get(0),0,5)];}
 public boolean working(){return state.get(1)>0;}public int progress(){return state.get(2);}public int water(){return state.get(3);}public boolean unlocked(){return state.get(4)>0;}
 public static void register(){}
 public static void open(ServerPlayerEntity p,WorkstationEntity e){
  if(e.demonstration){e.use(p);if(e.demonstration)return;}
  p.openHandledScreen(new NamedScreenHandlerFactory(){
   public Text getDisplayName(){return Text.literal(e.kind().title);}
   public ScreenHandler createMenu(int sync,PlayerInventory inv,PlayerEntity player){
    Inventory storage=new Inventory(){
     public int size(){return 5;}public boolean isEmpty(){for(int i=0;i<5;i++)if(!getStack(i).isEmpty())return false;return true;}
     public ItemStack getStack(int slot){return switch(slot){case 0->e.input;case 1->e.fuel;case 2->e.handle;case 3->e.core;case 4->e.output;default->ItemStack.EMPTY;};}
     public void setStack(int slot,ItemStack stack){switch(slot){case 0->e.input=stack;case 1->e.fuel=stack;case 2->e.handle=stack;case 3->e.core=stack;case 4->e.output=stack;}markDirty();}
     public ItemStack removeStack(int slot,int count){var result=getStack(slot).split(count);markDirty();return result;}
     public ItemStack removeStack(int slot){var result=getStack(slot);setStack(slot,ItemStack.EMPTY);return result;}
     public void markDirty(){e.changed();}public boolean canPlayerUse(PlayerEntity player){return e.canInteract(player);}
     public void clear(){for(int i=0;i<5;i++)setStack(i,ItemStack.EMPTY);}
    };
    PropertyDelegate values=new PropertyDelegate(){public int size(){return 6;}public void set(int i,int v){}public int get(int i){return switch(i){case 0->e.kind().ordinal();case 1->e.working()?1:0;case 2->Math.round(e.progress(0)*100);case 3->e.water;case 4->FoundryRecipe.unlocked(e.getWorld())?1:0;default->e.demonstration?1:0;};}};
    return new WorkstationMenu(sync,inv,e,storage,values);
   }
  });
 }
 public boolean canUse(PlayerEntity player){return station==null||station.canInteract(player)&&station.getWorld().getBlockEntity(station.getPos())==station;}
 public boolean onButtonClick(PlayerEntity player,int button){
  if(station==null||!(player instanceof ServerPlayerEntity p)||!canUse(player))return false;
  if(button==1&&station.kind()==FoundryContent.Station.QUENCH&&!station.working()&&station.water<8){
   for(int i=0;i<p.getInventory().size();i++){var stack=p.getInventory().getStack(i);if(stack.isOf(Items.WATER_BUCKET)&&stack.getCount()==1){p.getInventory().setStack(i,new ItemStack(Items.BUCKET));station.water=8;station.changed();return true;}}return false;
  }
  if(button!=0)return false;station.begin(p);if(station.working())p.closeHandledScreen();return true;
 }
 public ItemStack quickMove(PlayerEntity player,int index){
  if(index<0||index>=slots.size())return ItemStack.EMPTY;var slot=slots.get(index);if(!slot.hasStack()||!slot.canTakeItems(player))return ItemStack.EMPTY;
  var stack=slot.getStack();var original=stack.copy();
  if(index<5){if(!insertItem(stack,5,41,true))return ItemStack.EMPTY;}
  else{boolean moved=false;for(int i=0;i<4;i++)if(slots.get(i).canInsert(stack)&&insertItem(stack,i,i+1,false)){moved=true;break;}if(!moved)return ItemStack.EMPTY;}
  if(stack.isEmpty())slot.setStack(ItemStack.EMPTY);else slot.markDirty();slot.onTakeItem(player,stack);return original;
 }
}
