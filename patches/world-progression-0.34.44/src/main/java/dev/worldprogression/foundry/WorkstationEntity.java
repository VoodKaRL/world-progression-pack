package dev.worldprogression.foundry;

import java.util.*;
import net.minecraft.block.*;
import net.minecraft.block.entity.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.*;
import net.minecraft.nbt.*;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.*;
import net.minecraft.text.Text;
import net.minecraft.util.*;
import net.minecraft.util.math.*;

/** Transaction commits once, only at completion. Inputs remain recoverable on cancellation/restart. */
public final class WorkstationEntity extends BlockEntity {
 public static final double INTERACTION_RANGE_SQUARED=9.0;
 public ItemStack input=ItemStack.EMPTY,output=ItemStack.EMPTY,fuel=ItemStack.EMPTY,handle=ItemStack.EMPTY,core=ItemStack.EMPTY;
 public long started;public int duration,water;public boolean demonstration;public UUID artisan;
 private int lastStrike;private String processId="";private ItemStack prepared=ItemStack.EMPTY;private boolean demoSupplies;
 private String result="";private int resultCount;private long lastUse=-100,lastBeat=-1,demoEnd;
 public WorkstationEntity(BlockPos pos,BlockState state){super(FoundryContent.WORKSTATION,pos,state);}
 public boolean canInteract(PlayerEntity player){return world!=null&&player.getWorld()==world&&player.isAlive()&&!player.isSpectator()&&player.squaredDistanceTo(Vec3d.ofCenter(pos))<=INTERACTION_RANGE_SQUARED;}
 public FoundryContent.Station kind(){return ((WorkstationBlock)getCachedState().getBlock()).station;}
 public boolean working(){return duration>0;}
 public float progress(float delta){return world==null||!working()?0:Math.clamp((world.getTime()-started+delta)/duration,0,1);}
 public String instruction(){return switch(kind()){
  case DISMANTLE->"Coloca una herramienta o armadura usada. Vuelve a usar la mesa para desmontarla.";
  case FURNACE->"Con carbón o resina: funde 2 cargas biocerámicas para las placas Octopus. También puedes fundir 9 restos de hierro u oro para reciclar equipo.";
  case ANVIL->armorInput()?armorInstruction():fuel.isOf(FoundryContent.item("sword_mould"))?"Molde de espada: coloca un palo y 2 crisoles del mismo metal; vuelve a usar el yunque. Enfría la espada antes de usarla.":"Octopus: coloca primero una pieza de diamante y añade placas, tejido y núcleo. Para preparar las placas, martilla 2 crisoles biocerámicos. También se trabaja hierro y oro con un molde de espada.";
  case QUENCH->"Llena con un cubo de agua. Enfría 2 placas biocerámicas calientes para terminar las placas Octopus. Cada lote consume 2 de las 8 cargas del cubo.";
  case RACK->"Octopus: tensa 2 madejas biocompuestas y recoge 2 tejidos. Se preparan con seda de araña, membrana de slime, fibra meteórica, catalizador y resina de la prensa. La receta está en G.";
  case PRESS->"Prensa 2 cristales de miel para obtener resina, necesaria para el tejido Octopus. Después prepara el núcleo en la mesa central (receta en G) y prénsalo aquí para estabilizarlo.";
 };}
 private record Process(int required,String item,int count){}
 private Process recipe(ItemStack stack){
  if(stack.isEmpty())return null;var workshop=customFor(stack);if(workshop.isPresent()){var r=workshop.get().value();return new Process(r.input().count(),Registries.ITEM.getId(r.result().getItem()).toString(),r.result().getCount());}String id=Registries.ITEM.getId(stack.getItem()).toString();
  return switch(kind()){
   case DISMANTLE->Reclamation.salvage(id,stack.getDamage(),stack.getMaxDamage()).map(v->new Process(1,"worldprogression:"+v.material(),v.count())).orElse(null);
   case FURNACE->stack.isOf(FoundryContent.item("iron_reclaim"))?new Process(9,"worldprogression:molten_iron",1):stack.isOf(FoundryContent.item("gold_reclaim"))?new Process(9,"worldprogression:molten_gold",1):null;
   case ANVIL->stack.isOf(FoundryContent.item("molten_iron"))?new Process(fuel.isEmpty()?1:2,fuel.isEmpty()?"worldprogression:hot_iron":"worldprogression:hot_iron_sword",1):stack.isOf(FoundryContent.item("molten_gold"))?new Process(fuel.isEmpty()?1:2,fuel.isEmpty()?"worldprogression:hot_gold":"worldprogression:hot_gold_sword",1):null;
   case QUENCH->stack.isOf(FoundryContent.item("hot_iron_sword"))?new Process(1,"minecraft:iron_sword",1):stack.isOf(FoundryContent.item("hot_gold_sword"))?new Process(1,"minecraft:golden_sword",1):stack.isOf(FoundryContent.item("hot_iron"))?new Process(1,"minecraft:iron_ingot",1):stack.isOf(FoundryContent.item("hot_gold"))?new Process(1,"minecraft:gold_ingot",1):null;
   case RACK->id.equals("worldprogression:spider_silk")?new Process(4,"worldprogression:silk_cloth",2):null;
   case PRESS->id.equals("worldprogression:loot_honey_crystal")?new Process(2,"worldprogression:pressed_resin",1):null;
  };
 }
 public void use(ServerPlayerEntity player){
  if(!canInteract(player))return;
  // Main/off hand callbacks, rapid repeated packets and two players cannot duplicate a transition.
  if(world.getTime()-lastUse<4)return;lastUse=world.getTime();var held=player.getMainHandStack();
  if(demonstration){resetDemo();changed();}
  if(player.isSneaking()){
   if(!held.isEmpty()){say(player,"Agáchate con la mano vacía para retirar el material o cancelar el trabajo.");return;}
   if(working()&&artisan!=null&&!artisan.equals(player.getUuid())){say(player,"Otra persona está trabajando aquí. Espera a que termine.");return;}
   duration=0;artisan=null;result="";resultCount=0;processId="";prepared=ItemStack.EMPTY;take(player,output);if(output.isEmpty())take(player,input);if(input.isEmpty()&&output.isEmpty()){take(player,handle);if(handle.isEmpty())take(player,fuel);if(fuel.isEmpty())take(player,core);}changed();return;
  }
  if(working()){say(player,"El material se está trabajando. Puedes moverte mientras termina.");return;}
  if(!output.isEmpty()){take(player,output);changed();return;}
  if(armorSupply(player,held))return;
  if(armorInput()&&(held.isOf(FoundryContent.item("sword_mould"))||held.isOf(Items.STICK))){say(player,armorInstruction());return;}
  if(kind()==FoundryContent.Station.ANVIL&&held.isOf(FoundryContent.item("sword_mould"))){
   if(!input.isEmpty()||!fuel.isEmpty()){say(player,"Retira primero el material y el molde actuales.");return;}fuel=held.split(1);changed();say(player,"Molde de espada colocado. Añade un palo y 2 crisoles del mismo metal.");return;
  }
  if(kind()==FoundryContent.Station.ANVIL&&held.isOf(Items.STICK)){
   if(!handle.isEmpty()){say(player,"Ya hay un mango preparado.");return;}handle=held.split(1);changed();say(player,"Mango preparado. Coloca el molde y 2 crisoles del mismo metal.");return;
  }
  if(kind()==FoundryContent.Station.QUENCH&&held.isOf(Items.WATER_BUCKET)){
   if(water>=8){say(player,"La artesa ya está llena.");return;}water=8;player.setStackInHand(net.minecraft.util.Hand.MAIN_HAND,ItemUsage.exchangeStack(held,player,new ItemStack(Items.BUCKET)));sound(SoundEvents.ITEM_BUCKET_EMPTY,.4f,1);changed();return;
  }
  if(kind()==FoundryContent.Station.FURNACE&&(held.isOf(Items.COAL)||held.isOf(Items.CHARCOAL)||held.isOf(FoundryContent.item("pressed_resin")))){
   if(!fuel.isEmpty()&&!ItemStack.areItemsEqual(fuel,held)||fuel.getCount()>=16){say(player,"El depósito ya tiene combustible. Puedes retirarlo agachado.");return;}
   if(fuel.isEmpty())fuel=held.split(1);else{fuel.increment(1);held.decrement(1);}changed();say(player,"Combustible: "+fuel.getCount()+". Coloca 2 cargas biocerámicas o 9 restos del mismo metal.");return;
  }
  if(input.isEmpty()){
   var process=recipe(held);if(process==null){say(player,instruction());return;}
   if(held.getCount()<process.required()){say(player,"Necesitas "+process.required()+" unidades juntas. "+instruction());return;}
   input=held.split(process.required());artisan=player.getUuid();changed();sound(SoundEvents.BLOCK_WOOD_PLACE,.25f,1.1f);say(player,"Material colocado. Vuelve a usar la estación para trabajar; agáchate con la mano vacía para retirarlo.");return;
  }
  begin(player);
 }
 public boolean acceptsInput(ItemStack stack){return recipe(stack)!=null;}
 public void begin(ServerPlayerEntity player){
  if(world==null||world.isClient||!canInteract(player)||working()||demonstration||!output.isEmpty())return;
  var process=recipe(input);if(process==null){say(player,"Retira el material agachado con la mano vacía.");return;}
  if(input.getCount()<process.required()){say(player,"Faltan materiales: necesitas "+process.required()+" unidades en la ranura de entrada.");return;}
  var custom=customFor(input);if(custom.isPresent()){startCustom(player,custom.get());return;}
  if(kind()==FoundryContent.Station.ANVIL&&!fuel.isEmpty()&&(input.getCount()<2||handle.isEmpty())){say(player,"La espada requiere un mango de palo y 2 crisoles del mismo metal.");return;}
  if(kind()==FoundryContent.Station.FURNACE&&fuel.isEmpty()){say(player,"Falta combustible: carbón, carbón vegetal o resina prensada.");return;}
  if(kind()==FoundryContent.Station.QUENCH&&water<=0){say(player,"Llena primero la artesa con un cubo de agua.");return;}
  if(kind()==FoundryContent.Station.FURNACE)fuel.decrement(1);
  if(kind()==FoundryContent.Station.QUENCH)water--;
  processId="";prepared=ItemStack.EMPTY;result=process.item();resultCount=process.count();started=world.getTime();duration=kind().ticks;artisan=player.getUuid();lastBeat=-1;lastStrike=0;changed();FoundryNetwork.work((ServerWorld)world,this,player);say(player,"En marcha · quédate quieto mientras trabajo.");
 }
 private void take(ServerPlayerEntity p,ItemStack stack){if(!stack.isEmpty()){int count=stack.getCount();p.getInventory().insertStack(stack);p.currentScreenHandler.sendContentUpdates();if(stack.getCount()==count)say(p,"Haz espacio en tu inventario; el material sigue en la estación.");}}
 public boolean demonstrate(ServerPlayerEntity player){
  if(!input.isEmpty()||!output.isEmpty()||!fuel.isEmpty()||!handle.isEmpty()||!core.isEmpty()||working())return false;
  demonstration=true;started=world.getTime();duration=kind().ticks;artisan=null;lastBeat=-1;lastStrike=0;
  ItemStack held=player.getMainHandStack();String sample=switch(kind()){case DISMANTLE->"minecraft:iron_sword";case FURNACE->"worldprogression:chlorophyll_charge";case ANVIL->isDiamondArmor(held)?Registries.ITEM.getId(held.getItem()).toString():"worldprogression:molten_bioceramic";case QUENCH->"worldprogression:hot_bioceramic_plate";case RACK->"worldprogression:unbound_biofiber";case PRESS->held.isOf(FoundryContent.item("unsealed_radiant_core"))?"worldprogression:unsealed_radiant_core":"worldprogression:loot_honey_crystal";};
  if(!FoundryRecipe.unlocked(world))sample=switch(kind()){case DISMANTLE->"minecraft:iron_sword";case FURNACE->"worldprogression:iron_reclaim";case ANVIL->"worldprogression:molten_iron";case QUENCH->"worldprogression:hot_iron";case RACK->"worldprogression:spider_silk";case PRESS->"worldprogression:loot_honey_crystal";};
  input=new ItemStack(Registries.ITEM.get(Identifier.of(sample)),kind()==FoundryContent.Station.DISMANTLE||isDiamondArmor(held)&&kind()==FoundryContent.Station.ANVIL?1:2);
  var custom=customFor(input);if(custom.isPresent()){
   var r=custom.get().value();input.setCount(r.input().count());fuel=sample(r.additive());handle=sample(r.binding());core=sample(r.catalyst());demoSupplies=true;processId=custom.get().id().toString();prepared=r.craft(stationInput(),world.getRegistryManager());
  }else{processId="";prepared=ItemStack.EMPTY;var legacy=recipe(input);if(legacy!=null)input.setCount(legacy.required());}
  say(player,"Demostración · "+instruction());changed();return true;
 }
 private static ItemStack sample(FoundryRecipe.Cost cost){var choices=cost.ingredient().getMatchingStacks();return cost.count()==0||choices.length==0?ItemStack.EMPTY:choices[0].copyWithCount(cost.count());}
 private void resetDemo(){if(demonstration){input=ItemStack.EMPTY;output=ItemStack.EMPTY;duration=0;demonstration=false;artisan=null;processId="";prepared=ItemStack.EMPTY;if(demoSupplies){fuel=handle=core=ItemStack.EMPTY;}demoSupplies=false;}}
 public static boolean isDiamondArmor(ItemStack stack){return stack.isOf(Items.DIAMOND_HELMET)||stack.isOf(Items.DIAMOND_CHESTPLATE)||stack.isOf(Items.DIAMOND_LEGGINGS)||stack.isOf(Items.DIAMOND_BOOTS);}
 public boolean armorInput(){return kind()==FoundryContent.Station.ANVIL&&(isDiamondArmor(input)||input.isIn(net.minecraft.registry.tag.ItemTags.FOOT_ARMOR));}
 public ItemStack displayResult(){return prepared;}
 private FoundryRecipe.Input stationInput(){return new FoundryRecipe.Input(kind(),input,kind()==FoundryContent.Station.FURNACE?ItemStack.EMPTY:fuel,handle,core);}
 private Optional<net.minecraft.recipe.RecipeEntry<FoundryRecipe>> customFor(ItemStack stack){
  if(!(world instanceof ServerWorld w)||stack.isEmpty())return Optional.empty();
  var choices=w.getRecipeManager().listAllOfType(FoundryRecipe.TYPE).stream().filter(e->e.value().station()==kind()&&e.value().input().ingredient().test(stack)).sorted(java.util.Comparator.comparing(e->e.id().toString())).toList();
  return choices.stream().filter(e->!fuel.isEmpty()&&e.value().additive().ingredient().test(fuel)).findFirst().or(()->choices.stream().filter(e->!e.value().lining()).findFirst()).or(()->choices.stream().findFirst());
 }
 private static int missing(FoundryRecipe.Cost cost,ItemStack supplied){return Math.max(0,cost.count()-(cost.ingredient().test(supplied)?supplied.getCount():0));}
 private String armorInstruction(){
  var recipe=customFor(input);if(recipe.isEmpty())return "La mejora Octopus se desbloquea en la etapa 2, después del Rey Slime.";var r=recipe.get().value();if(r.lining()&&dev.worldprogression.survival.GelLiningRecipe.charges(input)>0)return "Estas botas ya tienen un forro activo. No se consumirá otro forro.";var pending=new ArrayList<String>();int plates=missing(r.additive(),fuel),cloth=missing(r.binding(),handle),cores=missing(r.catalyst(),core);
  if(plates>0)pending.add(plates+(r.lining()?" forro gelatinoso":" placas biocerámicas"));if(cloth>0)pending.add(cloth+" tejidos");if(cores>0)pending.add(cores+" núcleo biorreactor");
  return pending.isEmpty()?r.lining()?"Materiales preparados. Usa el yunque para fijar el forro; conservarás tus botas.":"Materiales preparados. Vuelve a usar el yunque para montar Octopus. Se conservan los encantamientos y el desgaste del diamante.":"Por colocar: "+String.join(", ",pending)+". Añádelos sobre el yunque; después vuelve a usarlo para montar la armadura.";
 }
 private boolean armorSupply(ServerPlayerEntity player,ItemStack held){
  if(kind()!=FoundryContent.Station.ANVIL)return false;
  String id=Registries.ITEM.getId(held.getItem()).toString();int slot=switch(id){case "worldprogression:gel_lining","worldprogression:octopus_chlorophyll_plate"->1;case "worldprogression:octopus_biofiber"->2;case "worldprogression:octopus_radiant_core"->3;default->0;};if(slot==0)return false;
  if(!armorInput()){say(player,"Coloca primero la pieza de diamante que quieres mejorar.");return true;}
  var found=held.isOf(dev.worldprogression.survival.SurvivalContent.GEL_LINING)?((ServerWorld)world).getRecipeManager().listAllOfType(FoundryRecipe.TYPE).stream().filter(e->e.value().station()==kind()&&e.value().lining()&&e.value().input().ingredient().test(input)).findFirst():customFor(input);if(found.isEmpty()){say(player,armorInstruction());return true;}if(!found.get().value().costs().get(slot).ingredient().test(held)){say(player,"Ese material no corresponde a la mejora de esta pieza. Pulsa O sobre el objeto para ver su proceso.");return true;}int needed=found.get().value().costs().get(slot).count();ItemStack current=slot==1?fuel:slot==2?handle:core;
  if(!current.isEmpty()&&!ItemStack.areItemsAndComponentsEqual(current,held)){say(player,"Retira primero el material anterior con la mano vacía y agachado.");return true;}
  int count=Math.min(held.getCount(),needed-current.getCount());if(count<=0){say(player,"Ya está preparada esa cantidad. "+armorInstruction());return true;}
  if(current.isEmpty()){current=held.split(count);if(slot==1)fuel=current;else if(slot==2)handle=current;else core=current;}else{current.increment(count);held.decrement(count);}changed();say(player,armorInstruction());return true;
 }
 private void startCustom(ServerPlayerEntity player,net.minecraft.recipe.RecipeEntry<FoundryRecipe> entry){
  var r=entry.value();if(!r.matches(stationInput(),world)){say(player,armorInput()?armorInstruction():"Retira los materiales incompatibles. "+instruction());return;}
  if(kind()==FoundryContent.Station.FURNACE&&fuel.getCount()<r.fuelCost()){say(player,"Falta combustible: carbón, carbón vegetal o resina.");return;}
  if(water<r.waterCost()){say(player,"Necesitas "+r.waterCost()+" cargas de agua. Un cubo repone 8.");return;}
  prepared=r.craft(stationInput(),world.getRegistryManager());processId=entry.id().toString();result="";resultCount=0;
  if(kind()==FoundryContent.Station.FURNACE)fuel.decrement(r.fuelCost());water-=r.waterCost();started=world.getTime();duration=kind().ticks;artisan=player.getUuid();lastBeat=-1;lastStrike=0;changed();FoundryNetwork.work((ServerWorld)world,this,player);say(player,r.upgrade()?"Voy a unir el tejido, las placas y el núcleo a tu pieza de diamante.":"En marcha · quédate quieto mientras trabajo. "+instruction());
 }
 private boolean finishCustom(ServerWorld w){
  var id=Identifier.tryParse(processId);var entry=id==null?Optional.<net.minecraft.recipe.RecipeEntry<?>>empty():w.getRecipeManager().get(id);var in=stationInput();
  if(entry.isEmpty()||!(entry.get().value() instanceof FoundryRecipe r)||!r.matches(in,w)||!ItemStack.areItemsAndComponentsEqual(prepared,r.craft(in,w.getRegistryManager()))||prepared.getCount()!=r.result().getCount()){
   duration=0;processId="";prepared=ItemStack.EMPTY;changed();return false;
  }
  output=prepared.copy();for(int i=0;i<4;i++){int count=r.costs().get(i).count();if(count>0)in.getStackInSlot(i).decrement(count);}return true;
 }
 public void tick(ServerWorld w){
  WorkstationBlock.parts(w,pos);
  if(!working()){if(demonstration&&w.getTime()>=demoEnd){resetDemo();changed();}return;}long age=w.getTime()-started;
  if(age<0){started=w.getTime();changed();age=0;}
  int beat=(int)(age/20);if(beat!=lastBeat){lastBeat=beat;switch(kind()){
   case DISMANTLE->{}
   case FURNACE->sound(SoundEvents.BLOCK_FURNACE_FIRE_CRACKLE,.22f,.85f);
   case ANVIL->{} // Strikes align to the downward swing, not the start of the cycle.
   case PRESS->{if(beat==1)sound(SoundEvents.BLOCK_PISTON_EXTEND,.13f,.85f);}
   case QUENCH->{}
   case RACK->sound(SoundEvents.BLOCK_WOOL_HIT,.12f,1.25f);
  }}
  if(kind()==FoundryContent.Station.QUENCH&&age==7)sound(SoundEvents.BLOCK_FIRE_EXTINGUISH,.18f,1.15f);
  boolean bench=kind()==FoundryContent.Station.DISMANTLE;
  if(bench||kind()==FoundryContent.Station.ANVIL){
   int passed=WorkshopMotion.strikesPassed(age,bench);
   if(passed>lastStrike){int at=WorkshopMotion.strikes(bench)[passed-1];if(age-at<=2)sound(bench?SoundEvents.BLOCK_WOOD_HIT:SoundEvents.BLOCK_ANVIL_LAND,bench?.20f:.14f,passed==3?1.04f:1.16f);lastStrike=passed;markDirty();}
  }
  if(age<duration)return;
  if(demonstration){var sample=recipe(input);output=!prepared.isEmpty()?prepared.copy():sample==null?ItemStack.EMPTY:new ItemStack(Registries.ITEM.get(Identifier.of(sample.item())),sample.count());input=ItemStack.EMPTY;duration=0;demoEnd=w.getTime()+70;changed();return;}
  // No item entities, no hopper-accessible ghost slots and no re-evaluation after consumption.
  if(!processId.isEmpty()){if(!finishCustom(w))return;}
  else{var id=Identifier.tryParse(result);if(id==null||!Registries.ITEM.containsId(id)){duration=0;changed();return;}output=new ItemStack(Registries.ITEM.get(id),Math.clamp(resultCount,1,64));if(result.endsWith("_sword")&&kind()==FoundryContent.Station.ANVIL)handle.decrement(1);var consumed=recipe(input);input.decrement(consumed==null?input.getCount():consumed.required());}
  duration=0;result="";resultCount=0;processId="";prepared=ItemStack.EMPTY;
  if(artisan!=null){var p=w.getServer().getPlayerManager().getPlayer(artisan);if(p!=null){teach(p);if(p.getWorld()==w&&p.squaredDistanceTo(Vec3d.ofCenter(pos))<64)say(p,"Listo · "+output.getName().getString()+" ×"+output.getCount()+". Usa la estación para recogerlo.");}}
  artisan=null;changed();sound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME,.16f,1.3f);
 }
 private void teach(ServerPlayerEntity p){dev.worldprogression.ecology.AdventureJournal.remember(p,"foundry:"+kind().id,kind().title,instruction()+" Los materiales permanecen visibles y guardados en la estación. Agáchate con la mano vacía para retirar materiales. La recuperación depende del desgaste y devuelve como máximo la mitad de los componentes originales; no conserva encantamientos. Puedes fabricar las estaciones pequeñas para tu base. El horno especial está en el taller de los slimes.");}
 public void scatter(){if(demonstration)resetDemo();for(var stack:List.of(input,output,fuel,handle,core))if(!stack.isEmpty())net.minecraft.util.ItemScatterer.spawn(world,pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5,stack);input=output=fuel=handle=core=ItemStack.EMPTY;}
 private void sound(SoundEvent e,float volume,float pitch){world.playSound(null,pos,e,SoundCategory.BLOCKS,volume,pitch);}
 private void say(ServerPlayerEntity p,String s){FoundrySpeech.at(p,pos,s);}
 public void changed(){markDirty();if(world!=null)world.updateListeners(pos,getCachedState(),getCachedState(),Block.NOTIFY_LISTENERS);}
 public BlockEntityUpdateS2CPacket toUpdatePacket(){return BlockEntityUpdateS2CPacket.create(this);}
 public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup lookup){return createNbt(lookup);}
 protected void writeNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){super.writeNbt(n,lookup);n.put("Input",input.encodeAllowEmpty(lookup));n.put("Output",output.encodeAllowEmpty(lookup));n.put("Fuel",fuel.encodeAllowEmpty(lookup));n.put("Handle",handle.encodeAllowEmpty(lookup));n.put("Core",core.encodeAllowEmpty(lookup));n.put("Prepared",prepared.encodeAllowEmpty(lookup));n.putString("Process",processId);n.putBoolean("DemoSupplies",demoSupplies);n.putInt("LastStrike",lastStrike);n.putLong("Started",started);n.putInt("Duration",duration);n.putInt("Water",water);n.putBoolean("Demo",demonstration);n.putLong("DemoEnd",demoEnd);n.putString("Result",result);n.putInt("ResultCount",resultCount);if(artisan!=null)n.putUuid("Artisan",artisan);}
 protected void readNbt(NbtCompound n,RegistryWrapper.WrapperLookup lookup){super.readNbt(n,lookup);input=ItemStack.fromNbtOrEmpty(lookup,n.getCompound("Input"));output=ItemStack.fromNbtOrEmpty(lookup,n.getCompound("Output"));fuel=ItemStack.fromNbtOrEmpty(lookup,n.getCompound("Fuel"));handle=ItemStack.fromNbtOrEmpty(lookup,n.getCompound("Handle"));core=ItemStack.fromNbtOrEmpty(lookup,n.getCompound("Core"));prepared=ItemStack.fromNbtOrEmpty(lookup,n.getCompound("Prepared"));processId=n.getString("Process");demoSupplies=n.getBoolean("DemoSupplies");started=n.getLong("Started");lastStrike=n.getInt("LastStrike");duration=Math.clamp(n.getInt("Duration"),0,400);water=Math.clamp(n.getInt("Water"),0,8);demonstration=n.getBoolean("Demo");demoEnd=n.getLong("DemoEnd");result=n.getString("Result");resultCount=n.getInt("ResultCount");artisan=n.containsUuid("Artisan")?n.getUuid("Artisan"):null;}
}
