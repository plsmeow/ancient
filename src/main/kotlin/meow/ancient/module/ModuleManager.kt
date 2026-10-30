package meow.ancient.module

import meow.ancient.client.extensions.Category
import meow.ancient.client.interfaces.ILoadable
import meow.ancient.module.modules.combat.AutoCartModule
import meow.ancient.module.modules.combat.BowBombModule
import meow.ancient.module.modules.combat.AutoMaceModule
import meow.ancient.module.modules.combat.BreachSwapModule
import meow.ancient.module.modules.combat.CriticalsModule
import meow.ancient.module.modules.combat.HitAuraModule
import meow.ancient.module.modules.combat.MaceKillModule
import meow.ancient.module.modules.combat.TriggerBotModule
import meow.ancient.module.modules.combat.VelocityModule
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
import meow.ancient.module.modules.other.TestModule
import meow.ancient.module.modules.player.ElytraHelperModule
import meow.ancient.module.modules.player.NoDelayModule
import meow.ancient.module.modules.render.ClickGuiModule
import meow.ancient.module.modules.render.HudModule
import meow.ancient.module.modules.render.TagsModule

object ModuleManager : ILoadable {
    val modules: MutableList<Module> = ArrayList()

    override fun load() {
        modules.clear()
        add(
            HitAuraModule,
            AutoCartModule,
            TriggerBotModule,
            AutoMaceModule,
            BowBombModule,
            CriticalsModule,
            BreachSwapModule,
            VelocityModule,
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
            NoDelayModule,
            ElytraHelperModule,
            TestModule,
            ClickGuiModule,
            HudModule,
            TagsModule
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
