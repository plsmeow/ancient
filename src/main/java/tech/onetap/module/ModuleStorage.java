package tech.onetap.module;

import com.google.common.eventbus.Subscribe;
import com.google.gson.JsonObject;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;
import tech.onetap.Onetap;
import tech.onetap.event.EventGameUpdate;
import tech.onetap.event.list.EventHUD;
import tech.onetap.event.list.EventKeyInput;
import tech.onetap.event.list.EventTick;
import tech.onetap.module.list.combat.*;
import tech.onetap.module.list.misc.*;
import tech.onetap.module.list.movement.*;
import tech.onetap.module.list.player.*;
import tech.onetap.module.list.render.*;
import tech.onetap.module.list.render.hud.Interface;
import tech.onetap.module.settings.BooleanSetting;
import tech.onetap.module.settings.ThemeSetting;
import tech.onetap.util.IMinecraft;
import tech.onetap.util.base.Instance;
import tech.onetap.util.party.connection.PartyApiClient;
import tech.onetap.util.player.other.SlownessManager;
import tech.onetap.util.rotation.Rotation;
import tech.onetap.util.rotation.RotationComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
public class ModuleStorage implements IMinecraft {
    private final List<Module> modules = new ArrayList<>();

    public void injectRegisterModules() {
        modules.addAll(List.of(
new FullBright(), new ClickGui(), new Optimization(), new Sprint(), new Troll(),
                  new TriggerBot(), new Criticals(), new MaceKill(), new BoatAura(), new TpAura(),
                new NoRender(), new KillAura(), new AutoFlyMace(),
                new Tags(), new TargetESP(), new NoPush(), new SoulESP(), new DragonFly(),
                new NoJumpDelay(), new TeleportBack(), new ElytraHelper(), new Flight(),
                new AutoTotem(), new ClickPearl(), new UseTracker(), new CrystalAura(), new Scaffold(),
                new ClientSounds(), new NoFriendDamage(), new ElytraBooster(),
                new FreeCamera(), new SwingAnimations(), new Predictions(), new HighJump(),
                new DogFly(), new AutoTpaccept(), new RPSpoofer(), new FireFly(), new AutoTrap(), new AutoPot(), new NoGround(),
                new AntiBot(), new AutoExplosion(), new DeathCoords(), new CrystalOptimizer(), new InstantRebreak(), new BowBomb(),
                new KillSay(), new KillSound(),
                new GuiMove(), new Tracers(), new ElytraMotion(), new Velocity(), new ElytraFlight(), new ElytraJump(),
                new ViewModel(), new KillEffect(), new AutoArmor(), new LonyHelper(), new FtHelper(), new Speed(), new GrimGlide(),
                new GrimStrafe(), new HWHelper(),
                new AutoTool(), new AirPlace(), new TapeMouse(), new Ambience(), new BlockOverlay(), new FreeLook(),
                new Trails(), new FastExp(), new NameProtect(), new CrystalSpammer(), new ChinaHat(),
                new AirStuck(), new AutoSwap(), new NoSlow(), new NoWeb(), new DiscordRPC(), new FakePlayer(), new Interface(), new AutoEat(),new AutoLeave(), new MoveFix(), new Hide(), new SpecCordExploit(),
                new BlockEsp(), new ScoreboardHealth(), new BoatNoClip(), new AutoCart()
        ));

        Onetap.getInstance().getEventBus().register(this);
    }

    public <T extends Module> T get(final String name) {
        return this.modules.stream()
                .filter(module -> module.getName().equalsIgnoreCase(name))
                .map(module -> (T) module)
                .findFirst()
                .orElse(null);
    }

    public <T extends Module> T get(final Class<T> clazz) {
        return this.modules.stream()
                .filter(module -> clazz.isAssignableFrom(module.getClass()))
                .map(clazz::cast)
                .findFirst()
                .orElse(null);
    }

    public List<Module> get(final ModuleCategory category) {
        return this.modules.stream()
                .filter(module -> module.getCategory() == category)
                .collect(Collectors.toList());
    }

    @Setter private float speedAcceleration;
    @Setter private float randomness;

    @Subscribe
    private void onGameUpdate(EventGameUpdate e) {
        if (mc.player == null) return;

        if (!SlownessManager.slowTasksIsEmpty()) SlownessManager.updateSlowTasks();
        if (!SlownessManager.timeTasksIsEmpty()) SlownessManager.updateTimeTasks(false);

        var aura = get(KillAura.class);

        if (!aura.isEnabled() || aura.getTarget() == null) {
            if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
                aura.lastYaw = (mc.gameRenderer.getCamera().getYaw() - 180);
                aura.lastPitch = -mc.gameRenderer.getCamera().getPitch();
            } else {
                aura.lastYaw = mc.gameRenderer.getCamera().getYaw();
                aura.lastPitch = mc.gameRenderer.getCamera().getPitch();
            }

            if (aura.rotation.is("Vanilla") || Instance.get(FreeLook.class).isActive()) return;

            updateBackwardsOther();
        }
    }

    @Subscribe
    private void onRender(EventHUD ignored) {
        for (var module : getModules()) {
            module.getAnimation().run(module.isEnabled());
            for (var setting : module.getSettings()) {
                if (setting instanceof BooleanSetting b) b.getAnimation().run(b.getValue());
                if (setting instanceof ThemeSetting t) t.getValue().animation.run(1);
            }
        }
    }

    @Subscribe
    private void onKey(EventKeyInput e) {
        if (Hide.isActive) return;
        if (e.getAction() != 1) return;
        for (var module : getModules())
            for (var setting : module.getSettings())
                if (setting.isBound() && e.getKey() == setting.getKey()) {
                    setting.triggerBind();
                    ClientSounds soundsModule = get(ClientSounds.class);
                    if (soundsModule != null && soundsModule.isEnabled()) {
                        ClientSounds.play(true);
                    }
                }
    }

    @Subscribe
    private void onUpdate(EventTick ignored) {
        if (!SlownessManager.timeTasksIsEmpty()) SlownessManager.updateTimeTasks(true);

        if (mc.player == null || true) return;

        PartyApiClient.fetchPartyStateAsync();
        PartyApiClient.fetchInvitesAsync();

        JsonObject j = new JsonObject();
        j.addProperty("player", mc.player.getNameForScoreboard());
        j.addProperty("x", mc.player.getX());
        j.addProperty("y", mc.player.getY());
        j.addProperty("z", mc.player.getZ());

        PartyApiClient.postAsync("/party/pos", j, json -> {});
    }

    private void updateBackwardsOther() {
        if(mc.player.isGliding()){
            speedAcceleration += 0.06f;

        }
        else{
            speedAcceleration += 0.006f;

        }
        get(KillAura.class).speedAcceleration = 0;

        var angle = new Rotation(mc.gameRenderer.getCamera().getYaw(), mc.gameRenderer.getCamera().getPitch());

        var deltaYaw = MathHelper.wrapDegrees(angle.getYaw() - mc.player.getYaw());
        var deltaPitch = angle.getPitch() - mc.player.getPitch();
        if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
            deltaYaw = MathHelper.wrapDegrees((angle.getYaw() - 180) - mc.player.getYaw());
            deltaPitch = MathHelper.wrapDegrees(-angle.getPitch() - mc.player.getPitch());
        }

        var smooth = Math.max(speedAcceleration, 0);

        var newYaw = mc.player.getYaw() + deltaYaw * (Math.min(Math.max(smooth, 0), 1));
        var newPitch = mc.player.getPitch() + deltaPitch * (Math.min(Math.max(smooth / 2, 0), 1));

        var smoothRot = new Rotation(newYaw, newPitch);

        RotationComponent.update(smoothRot, 360, 360, 360, 360, 0, 2, false);
    }
}
