package meow.ancient.module

import meow.ancient.client.extensions.Category
import meow.ancient.client.interfaces.ILoadable
import meow.ancient.module.modules.combat.BowBombModule
import meow.ancient.module.modules.combat.AntiBotModule
import meow.ancient.module.modules.combat.AutoTotemModule
import meow.ancient.module.modules.combat.AutoMaceModule
import meow.ancient.module.modules.combat.BreachSwapModule
import meow.ancient.module.modules.combat.CriticalsModule
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.MaceKillModule
import meow.ancient.module.modules.combat.TriggerBotModule
import meow.ancient.module.modules.combat.VelocityModule
import meow.ancient.module.modules.combat.SpearKillModule
import meow.ancient.module.modules.combat.HitBoxesModule
import meow.ancient.module.modules.combat.CrystalAuraModule
import meow.ancient.module.modules.combat.CrystalAuraTestModule
import meow.ancient.module.modules.combat.AnchorAuraModule
import meow.ancient.module.modules.combat.TpAuraModule
import meow.ancient.module.modules.combat.CrystalOptimizerModule
import meow.ancient.module.modules.combat.AimAssistModule
import meow.ancient.module.modules.movement.StepModule
import meow.ancient.module.modules.player.FastExpModule
import meow.ancient.module.modules.movement.AirStuckModule
import meow.ancient.module.modules.movement.ElytraBoosterModule
import meow.ancient.module.modules.movement.ElytraFlyModule
import meow.ancient.module.modules.movement.ElytraJumpModule
import meow.ancient.module.modules.movement.FlightModule
import meow.ancient.module.modules.movement.HighJumpModule
import meow.ancient.module.modules.movement.GrimGlideModule
import meow.ancient.module.modules.movement.ElytraMotionModule
import meow.ancient.module.modules.movement.AutoSprintModule
import meow.ancient.module.modules.movement.DragonFlyModule
import meow.ancient.module.modules.movement.GroundSpoofModule
import meow.ancient.module.modules.movement.InventoryMoveModule
import meow.ancient.module.modules.movement.NoSlowModule
import meow.ancient.module.modules.movement.SpeedModule
import meow.ancient.module.modules.movement.NoWebModule
import meow.ancient.module.modules.other.AntiCheatModule
import meow.ancient.module.modules.other.TestModule
import meow.ancient.module.modules.other.FtHelperModule
import meow.ancient.module.modules.other.HwHelperModule
import meow.ancient.module.modules.other.NeuroRecordModule
import meow.ancient.module.modules.player.ElytraHelperModule
import meow.ancient.module.modules.player.NoDelayModule
import meow.ancient.module.modules.player.AutoTpacceptModule
import meow.ancient.module.modules.player.ClickPearlModule
import meow.ancient.module.modules.player.FakePlayerModule
import meow.ancient.module.modules.player.NoPushModule
import meow.ancient.module.modules.player.TpLootModule
import meow.ancient.module.modules.render.ArrowsModule
import meow.ancient.module.modules.render.BlockEspModule
import meow.ancient.module.modules.render.ChamsModule
import meow.ancient.module.modules.render.CameraTweaksModule
import meow.ancient.module.modules.render.ClickGuiModule
import meow.ancient.module.modules.render.ClientSoundsModule
import meow.ancient.module.modules.render.FreeCameraModule
import meow.ancient.module.modules.render.FullBrightModule
import meow.ancient.module.modules.render.HudModule
import meow.ancient.module.modules.render.LightEspModule
import meow.ancient.module.modules.render.TagsModule
import meow.ancient.module.modules.render.NoRenderModule
import meow.ancient.module.modules.render.TargetEspModule
import meow.ancient.module.modules.render.ViewModelModule
import meow.ancient.module.modules.render.PredictionsModule

object ModuleManager : ILoadable {
    val modules: MutableList<Module> = ArrayList()

    override fun load() {
        modules.clear()
        add(
            HitAuraModule,
            AutoTotemModule,
            AntiBotModule,
            TriggerBotModule,
            AutoMaceModule,
            BowBombModule,
            CriticalsModule,
            BreachSwapModule,
            VelocityModule,
            SpearKillModule,
            HitBoxesModule,
            CrystalAuraModule,
            CrystalAuraTestModule,
            AnchorAuraModule,
            TpAuraModule,
            CrystalOptimizerModule,
            AimAssistModule,
            AutoSprintModule,
            GroundSpoofModule,
            AirStuckModule,
            ElytraBoosterModule,
            ElytraFlyModule,
            ElytraJumpModule,
            ElytraMotionModule,
            FlightModule,
            GrimGlideModule,
            HighJumpModule,
            DragonFlyModule,
            InventoryMoveModule,
            NoSlowModule,
            SpeedModule,
            NoWebModule,
            StepModule,
            NoDelayModule,
            FastExpModule,
            ElytraHelperModule,
            ClickPearlModule,
            AutoTpacceptModule,
            NoPushModule,
            FakePlayerModule,
            TpLootModule,
            AntiCheatModule,
            TestModule,
            FtHelperModule,
            HwHelperModule,
            NeuroRecordModule,
            ClickGuiModule,
            HudModule,
            TagsModule,
            FreeCameraModule,
            TargetEspModule,
            FullBrightModule,
            ArrowsModule,
            ViewModelModule,
            ClientSoundsModule,
            NoRenderModule,
            CameraTweaksModule,
            ChamsModule,
            BlockEspModule,
            LightEspModule,
            PredictionsModule,
        )
    }

    private fun add(vararg module: Module) {
        modules.addAll(module)
    }

    fun getByCategory(category: Category): List<Module> =
        modules.filter { it.category == category }

    fun getByName(name: String): Module? =
        modules.firstOrNull { it.name.equals(name, ignoreCase = true) }

    inline fun <reified T : Module> get(): T? =
        modules.firstOrNull { it is T } as? T
}
