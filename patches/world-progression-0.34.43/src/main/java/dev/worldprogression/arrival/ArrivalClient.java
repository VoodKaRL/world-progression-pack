package dev.worldprogression.arrival;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.model.*;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** First-person arrival sequence. The server owns starter item delivery and selection. */
public final class ArrivalClient {
 public static final int BOOK_TICKS=174, BAG_TICKS=70, HOLD_TICKS=24;
 private static boolean playing,pending,awaitingIntro,guideSelected,withBag,escapeDown;
 private static int age,pendingAge,stableTicks,holdTicks,hintTicks;
 private static float baseYaw,basePitch,initialHealth;
 private static Vec3d origin,lastPendingPosition;
 private static Perspective priorPerspective;
 private static final ArrivalTimeline timeline=new ArrivalTimeline();
 private static final java.util.List<ArrivalVoice> voices=new java.util.ArrayList<>();
 private static final Identifier WHITE=Identifier.of("minecraft","textures/block/white_concrete.png");
 private static ModelPart upperRight,lowerRight,upperLeft,lowerLeft;
 private static ModelPart covers,pages,spine,trim,mark;
 public record Pose(Vec3d camera,float yaw,float pitch,Vec3d book,Quaternionf bookRotation,Vec3d leftWrist,Vec3d rightWrist,double time){}
 public static boolean active(){return playing;}
 public static boolean blocking(){return playing||pending;}
 public static boolean preparing(){return pending;}
 public static int age(){return age;}
 public static int duration(){return BOOK_TICKS+(withBag?BAG_TICKS:0);}
 public static float skipProgress(){return holdTicks/(float)HOLD_TICKS;}
 public static boolean skipHint(){return playing&&hintTicks>0;}
 public static boolean bagVisible(){return withBag&&playing&&age<120;}
 public static double smooth(double a,double b,double t){double x=Math.clamp((t-a)/(b-a),0,1);return x*x*x*(x*(x*6-15)+10);}
 private static double lerp(double a,double b,double t){return a+(b-a)*t;}
 private static Vec3d mix(Vec3d a,Vec3d b,double t){return a.lerp(b,t);}
 public static void queue(boolean bag){if(playing)return;pending=true;awaitingIntro=false;withBag=bag;}
 public static void expect(){finish(false);pending=true;awaitingIntro=true;pendingAge=stableTicks=0;lastPendingPosition=null;withBag=false;KeyBinding.unpressAll();}
 public static void cancelPending(){pending=awaitingIntro=false;}
 public static boolean safe(MinecraftClient c){
  if(c.player==null||c.world==null||c.currentScreen!=null||c.getOverlay()!=null)return false;
  var p=c.player;
  if(!p.isAlive()||p.isSpectator()||!p.isOnGround()||p.isTouchingWater()||p.isInLava()||p.hasVehicle()||p.isFallFlying()||p.isSleeping()||p.hurtTime>0)return false;
  if(!c.world.getChunkManager().isChunkLoaded(p.getBlockX()>>4,p.getBlockZ()>>4))return false;
  for(int x:new int[]{-2,0,2})for(int z:new int[]{-2,0,2})if(!c.worldRenderer.isRenderingReady(p.getBlockPos().add(x,-1,z)))return false;
  return true;
 }
 public static void start(boolean bag){
  var c=MinecraftClient.getInstance();if(c.player==null||c.world==null)return;
  origin=c.player.getPos();baseYaw=c.player.getYaw();basePitch=c.player.getPitch();initialHealth=c.player.getHealth();age=0;holdTicks=hintTicks=0;timeline.reset();voices.clear();escapeDown=guideSelected=false;withBag=bag;priorPerspective=c.options.getPerspective();c.options.setPerspective(Perspective.FIRST_PERSON);playing=true;pending=false;KeyBinding.unpressAll();
 }
 public static void finish(boolean selectGuide){
  if(!playing)return;playing=false;escapeDown=false;holdTicks=hintTicks=0;var c=MinecraftClient.getInstance();KeyBinding.unpressAll();if(age<duration())voices.forEach(ArrivalVoice::release);voices.clear();
  if(c.player!=null){c.player.setYaw(baseYaw);c.player.setPitch(basePitch);}
  if(priorPerspective!=null)c.options.setPerspective(priorPerspective);
  if(selectGuide&&!guideSelected&&net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.worldprogression.item.WelcomeGuide.Select.ID)){guideSelected=true;net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.worldprogression.item.WelcomeGuide.Select());}
 }
 public static void escape(boolean down){if(!playing)return;escapeDown=down;hintTicks=65;if(!down)holdTicks=0;}
 public static void register(){
  ClientPlayConnectionEvents.INIT.register((handler,c)->expect());
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.worldprogression.item.WelcomeGuide.Intro.ID,(p,c)->c.client().execute(()->queue(p.bag())));
  net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(dev.worldprogression.item.WelcomeGuide.NoIntro.ID,(p,c)->c.client().execute(ArrivalClient::cancelPending));
  ClientPlayConnectionEvents.DISCONNECT.register((handler,c)->{finish(false);cancelPending();origin=null;});
  ClientTickEvents.START_CLIENT_TICK.register(c->{
   if(pending){
    KeyBinding.unpressAll();
    if(c.world!=null&&c.player!=null&&c.currentScreen==null&&c.getOverlay()==null){
     ++pendingAge;
     if(!c.player.isAlive()||c.player.isSpectator()){cancelPending();return;}
     if(lastPendingPosition!=null&&lastPendingPosition.squaredDistanceTo(c.player.getPos())<.000001)stableTicks++;else stableTicks=0;
     lastPendingPosition=c.player.getPos();
     // Keep the loading cover until the actual server greeting and terrain are ready.
     if(!awaitingIntro&&stableTicks>=3&&safe(c))start(withBag);
     // A server without the welcome protocol (or an older server that never
     // replied for returning players) must never leave the loading cover up.
     else if(awaitingIntro&&pendingAge>=100)cancelPending();
    }
   }
   if(!playing)return;
   if(c.player==null||c.world==null||!c.player.isAlive()||c.player.getHealth()<initialHealth||c.player.hurtTime>0||c.player.isTouchingWater()||c.player.isInLava()||c.player.hasVehicle()||c.currentScreen!=null||c.player.getPos().squaredDistanceTo(origin)>.12){finish(false);return;}
   KeyBinding.unpressAll();c.player.setYaw(baseYaw);c.player.setPitch(basePitch);
   if(hintTicks>0)hintTicks--;if(escapeDown){hintTicks=65;if(++holdTicks>=HOLD_TICKS){finish(true);return;}}else holdTicks=0;
   ++age;
   if(age>duration()-8&&!guideSelected&&net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.canSend(dev.worldprogression.item.WelcomeGuide.Select.ID)){guideSelected=true;net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new dev.worldprogression.item.WelcomeGuide.Select());}
   if(age>duration())finish(true);
  });
  WorldRenderEvents.AFTER_ENTITIES.register(context->{if(!playing||context.matrixStack()==null||context.consumers()==null)return;render(context.matrixStack(),context.consumers(),context.camera().getPos(),context.tickCounter().getTickDelta(false));});
 }
 private static void sounds(MinecraftClient c,double renderedSeconds){
  for(var cue:timeline.due(renderedSeconds,withBag)){
   var id=cue.sound.equals("support")?c.world.getBlockState(BlockPos.ofFloored(origin).down()).getSoundGroup().getStepSound().getId():Identifier.of("worldprogression","arrival_"+cue.sound);
   var voice=new ArrivalVoice(id,cue.volume);
   voices.add(voice);c.getSoundManager().play(voice);
  }
 }
 /** Final cover also masks other mods' HUD callbacks during the loading-to-arrival handoff. */
 public static void cover(net.minecraft.client.render.RenderTickCounter ticks){
  var c=MinecraftClient.getInstance();if(!blocking()||c.world==null||c.player==null)return;
  double t=playing?pose(ticks.getTickDelta(false)).time():0;
  int alpha=pending?255:(int)Math.round(255*(1-smooth(0,.30,t)));
  if(alpha<=0)return;
  var draw=new net.minecraft.client.gui.DrawContext(c,c.getBufferBuilders().getEntityVertexConsumers());draw.getMatrices().push();draw.getMatrices().translate(0,0,500);draw.fill(0,0,c.getWindow().getScaledWidth(),c.getWindow().getScaledHeight(),(alpha<<24)|0x101711);draw.draw();draw.getMatrices().pop();
 }
 public static void drawSkip(net.minecraft.client.gui.DrawContext c){
  if(!skipHint())return;var mc=MinecraftClient.getInstance();int w=c.getScaledWindowWidth(),h=c.getScaledWindowHeight(),x=w-174,y=h-39;c.fill(x-8,y-6,w-12,y+24,0xB0182119);c.drawTextWithShadow(mc.textRenderer,"Mantén Esc para saltar",x,y,0xFFE8E8D8);c.fill(x,y+15,w-21,y+18,0xFF53614D);c.fill(x,y+15,x+(int)((w-21-x)*skipProgress()),y+18,0xFFA8CF89);
 }
 public static Pose pose(float delta){
  double realTime=(Math.max(0,age-1)+delta)/20.0;
  double t=ArrivalTimeline.bookTime(realTime,withBag);
  Vec3d f=Vec3d.fromPolar(0,baseYaw),r=new Vec3d(-Math.cos(Math.toRadians(baseYaw)),0,-Math.sin(Math.toRadians(baseYaw)));
  double rise=smooth(.15,2.75,t),look=smooth(2.8,3.9,t),bend=smooth(3.65,4.65,t),stand=smooth(4.9,6.3,t),settle=smooth(7.0,8.55,t);
  double height=lerp(.50,1.62,rise)-.97*bend*(1-stand);
  double lean=bend*(1-stand);
  double sway=Math.sin(rise*Math.PI);
  Vec3d cam=origin.add(0,height+.025*Math.sin(rise*Math.PI*2),0).add(r.multiply(.42*lean+.055*sway)).add(f.multiply(.45*lean-.035*sway));
  float yaw=(float)(baseYaw+5*Math.sin(rise*Math.PI)+42*look*(1-smooth(5.15,6.25,t)));
  float pitch=(float)(lerp(46,10,rise)+2.5*Math.sin(rise*Math.PI*2)+45*look*(1-smooth(5.05,6.3,t)));
  pitch=(float)lerp(pitch,basePitch,settle);
  Vec3d forward=Vec3d.fromPolar(pitch,yaw),right=new Vec3d(-Math.cos(Math.toRadians(yaw)),0,-Math.sin(Math.toRadians(yaw))),up=right.crossProduct(forward).normalize();
  Vec3d ground=origin.add(r.multiply(.67)).add(f.multiply(.76)).add(0,.053,0);
  Vec3d presented=cam.add(forward.multiply(.60)).add(up.multiply(-.18-.72*settle)).add(right.multiply(.04+.29*settle));
  double pickup=smooth(ArrivalTimeline.BOOK_GRIP,ArrivalTimeline.BOOK_LIFT_END,t);
  Vec3d book=mix(ground,presented,pickup).add(r.multiply(.045*Math.sin(pickup*Math.PI))).add(0,.09*Math.sin(pickup*Math.PI),0);
  Quaternionf q0=new Quaternionf().rotateY((float)Math.toRadians(-baseYaw-12));
  Quaternionf q1=new Quaternionf().rotateY((float)Math.toRadians(-yaw)).rotateX((float)Math.toRadians(-90+pitch)).rotateY((float)Math.toRadians(-7));
  Quaternionf q=new Quaternionf(q0).slerp(q1,(float)pickup);
  Vec3d leftRest=cam.add(right.multiply(-.34)).add(up.multiply(-.48)).add(forward.multiply(.44));
  Vec3d rightRest=cam.add(right.multiply(.34)).add(up.multiply(-.48)).add(forward.multiply(.44));
  Vec3d leftGround=origin.add(r.multiply(-.25)).add(f.multiply(.28)).add(0,.065,0);
  Vec3d rightGround=origin.add(r.multiply(.27)).add(f.multiply(.33)).add(0,.065,0);
  Vec3d left=mix(leftGround,leftRest,smooth(.8,2.35,t));
  Vec3d rightHand=mix(rightGround,rightRest,smooth(1.0,2.55,t));
  Vector3f edgeR=new Vector3f(-.135f,-.01f,0).rotate(q),edgeL=new Vector3f(.135f,-.01f,0).rotate(q);
  Vec3d gripR=book.add(edgeR.x,edgeR.y,edgeR.z),gripL=book.add(edgeL.x,edgeL.y,edgeL.z);
  rightHand=mix(rightHand,gripR,smooth(ArrivalTimeline.BOOK_REACH,ArrivalTimeline.BOOK_GRIP,t));
  left=mix(left,gripL,smooth(4.9,ArrivalTimeline.BOOK_SECOND_HAND,t)*(1-smooth(7.0,7.7,t)));
  if(withBag&&realTime>=ArrivalTimeline.BAG_START&&realTime<ArrivalTimeline.BAG_END){
   double u=realTime-ArrivalTimeline.BAG_START,turn=smooth(0,.75,u)*(1-smooth(2.3,3.3,u)),crouch=smooth(.55,1.25,u)*(1-smooth(1.7,2.8,u));
   cam=origin.add(0,1.62-.82*crouch+.012*Math.sin(u*Math.PI)*turn,0).add(r.multiply(-.42*crouch-.035*Math.sin(turn*Math.PI))).add(f.multiply(.45*crouch));yaw=(float)(baseYaw-42*turn);pitch=(float)(10+40*turn);
   forward=Vec3d.fromPolar(pitch,yaw);right=new Vec3d(-Math.cos(Math.toRadians(yaw)),0,-Math.sin(Math.toRadians(yaw)));up=right.crossProduct(forward).normalize();
   left=cam.add(right.multiply(-.34)).add(up.multiply(-.48)).add(forward.multiply(.44));rightHand=cam.add(right.multiply(.34)).add(up.multiply(-.48)).add(forward.multiply(.44));
   Vec3d sack=bagPosition(cam,yaw,pitch,realTime);left=mix(left,sack.add(0,.34,0),smooth(ArrivalTimeline.BAG_REACH,ArrivalTimeline.BAG_GRIP,u));
   // The guide remains on the ground while the player handles the sack.
   book=ground;q=q0;
  }
  return new Pose(cam,yaw,pitch,book,q,left,rightHand,realTime);
 }
 public static Vec3d bagPosition(Vec3d camera,float yaw,float pitch,double time){
  Vec3d f=Vec3d.fromPolar(0,baseYaw),r=new Vec3d(-Math.cos(Math.toRadians(baseYaw)),0,-Math.sin(Math.toRadians(baseYaw)));
  Vec3d ground=origin.add(r.multiply(-.67)).add(f.multiply(.76));double u=time-ArrivalTimeline.BAG_START,pick=smooth(ArrivalTimeline.BAG_GRIP,ArrivalTimeline.BAG_LIFT_END,u),stash=smooth(ArrivalTimeline.BAG_STOW,ArrivalTimeline.BAG_STOW_END,u);
  Vec3d forward=Vec3d.fromPolar(pitch,yaw),right=new Vec3d(-Math.cos(Math.toRadians(yaw)),0,-Math.sin(Math.toRadians(yaw))),up=right.crossProduct(forward).normalize();
  Vec3d held=camera.add(forward.multiply(.68)).add(up.multiply(-.4-.65*stash)).add(right.multiply(-.10-.30*stash));return ground.lerp(held,pick);
 }
 private static ModelPart part(String name,int u,int v,float x,float y,float z,float w,float h,float d,int tw,int th){
  var data=new ModelData();data.getRoot().addChild(name,ModelPartBuilder.create().uv(u,v).cuboid(x,y,z,w,h,d),ModelTransform.NONE);return TexturedModelData.of(data,tw,th).createModel().getChild(name);
 }
 private static void models(){
  if(covers!=null)return;
  upperRight=part("arm",40,16,-2,0,-2,4,6,4,64,64);lowerRight=part("arm",40,22,-2,0,-2,4,6,4,64,64);
  upperLeft=part("arm",32,48,-2,0,-2,4,6,4,64,64);lowerLeft=part("arm",32,54,-2,0,-2,4,6,4,64,64);
  var data=new ModelData();var root=data.getRoot();
  root.addChild("covers",ModelPartBuilder.create().uv(0,0).cuboid(-2.74f,-.62f,-4.2f,5.89f,.28f,8.4f).cuboid(-2.74f,.34f,-4.2f,5.89f,.28f,8.4f),ModelTransform.NONE);
  root.addChild("pages",ModelPartBuilder.create().uv(0,0).cuboid(-2.74f,-.34f,-4.0f,5.64f,.68f,8.0f),ModelTransform.NONE);
  root.addChild("spine",ModelPartBuilder.create().uv(0,0).cuboid(-3.18f,-.62f,-4.2f,.44f,1.24f,8.4f),ModelTransform.NONE);
  var border=ModelPartBuilder.create().uv(0,0);
  for(int sign:new int[]{-1,1})border.cuboid(sign<0?-2.75f:2.45f,.626f,-3.65f,.3f,.025f,7.3f);
  border.cuboid(-2.75f,.626f,-3.65f,5.5f,.025f,.3f).cuboid(-2.75f,.626f,3.35f,5.5f,.025f,.3f);
  root.addChild("trim",border,ModelTransform.NONE);
  root.addChild("mark",ModelPartBuilder.create().uv(0,0).cuboid(-1.25f,.66f,.05f,.55f,.04f,1.3f).cuboid(-.75f,.66f,-.75f,.55f,.04f,.9f).cuboid(-.25f,.66f,-1.15f,.55f,.04f,.5f).cuboid(.25f,.66f,-.75f,.55f,.04f,.9f).cuboid(.75f,.66f,.05f,.55f,.04f,1.3f),ModelTransform.NONE);
  var baked=TexturedModelData.of(data,64,64).createModel();covers=baked.getChild("covers");pages=baked.getChild("pages");spine=baked.getChild("spine");trim=baked.getChild("trim");mark=baked.getChild("mark");
 }
 private static void segment(MatrixStack m,VertexConsumerProvider v,Vec3d camera,Vec3d a,Vec3d b,ModelPart model,Identifier skin,int light){
  Vec3d d=b.subtract(a);m.push();m.translate(a.x-camera.x,a.y-camera.y,a.z-camera.z);m.multiply(new Quaternionf().rotationTo(new Vector3f(0,1,0),d.toVector3f().normalize()));m.scale(.92f,(float)(d.length()/.375),.92f);model.render(m,v.getBuffer(RenderLayer.getEntityCutoutNoCull(skin)),light,OverlayTexture.DEFAULT_UV);m.pop();
 }
 private static void arm(MatrixStack m,VertexConsumerProvider v,Vec3d camera,Pose p,boolean right,int light){
  var c=MinecraftClient.getInstance();double sign=right?1:-1;Vec3d f=Vec3d.fromPolar(p.pitch(),p.yaw()),r=new Vec3d(-Math.cos(Math.toRadians(p.yaw())),0,-Math.sin(Math.toRadians(p.yaw()))),up=r.crossProduct(f).normalize();
  Vec3d bodyForward=Vec3d.fromPolar(0,baseYaw),bodyRight=new Vec3d(-Math.cos(Math.toRadians(baseYaw)),0,-Math.sin(Math.toRadians(baseYaw)));
  Vec3d shoulder=p.camera().add(bodyRight.multiply(sign*.37)).add(0,-.25,0).subtract(bodyForward.multiply(.06));
  Vec3d wrist=right?p.rightWrist():p.leftWrist();Vec3d direction=wrist.subtract(shoulder);double length=direction.length();
  Vec3d outward=bodyRight.multiply(sign).add(0,-.42,0);Vec3d normal=outward.subtract(direction.multiply(outward.dotProduct(direction)/Math.max(.001,length*length))).normalize();
  Vec3d elbow=shoulder.lerp(wrist,.5).add(normal.multiply(Math.sqrt(Math.max(.001,.39*.39-length*length*.25))));
  Identifier skin=c.player.getSkinTextures().texture();segment(m,v,camera,shoulder,elbow,right?upperRight:upperLeft,skin,light);segment(m,v,camera,elbow,wrist,right?lowerRight:lowerLeft,skin,light);
 }
 private static void render(MatrixStack m,VertexConsumerProvider v,Vec3d camera,float delta){
  var c=MinecraftClient.getInstance();if(c.player==null||c.world==null)return;models();Pose p=pose(delta);sounds(c,p.time());int light=WorldRenderer.getLightmapCoordinates(c.world,BlockPos.ofFloored(camera));
  arm(m,v,camera,p,false,light);arm(m,v,camera,p,true,light);
  if(bagVisible()){
   Vec3d at=bagPosition(p.camera(),p.yaw(),p.pitch(),p.time());m.push();m.translate(at.x-camera.x,at.y-camera.y,at.z-camera.z);m.multiply(new Quaternionf().rotateY((float)Math.toRadians(-baseYaw+8)));m.scale(.46f,.46f,.46f);m.translate(-.5,0,-.5);
   c.getBlockRenderManager().renderBlockAsEntity(dev.worldprogression.bag.PlacedBags.BLOCKS.getFirst().getDefaultState(),m,v,light,OverlayTexture.DEFAULT_UV);m.pop();
  }
  m.push();m.translate(p.book().x-camera.x,p.book().y-camera.y,p.book().z-camera.z);m.multiply(p.bookRotation());m.scale(.72f,.72f,.72f);
  var vertices=v.getBuffer(RenderLayer.getEntityCutoutNoCull(WHITE));covers.render(m,vertices,light,OverlayTexture.DEFAULT_UV,0xFF734D32);pages.render(m,vertices,light,OverlayTexture.DEFAULT_UV,0xFFE7DCC3);spine.render(m,vertices,light,OverlayTexture.DEFAULT_UV,0xFF472F22);trim.render(m,vertices,light,OverlayTexture.DEFAULT_UV,0xFFCBA466);mark.render(m,vertices,light,OverlayTexture.DEFAULT_UV,0xFF96C67E);m.pop();
 }
}
