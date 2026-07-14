package io.github.sweetzonzi.py_port;

import io.github.sweetzonzi.py_port.command.PyCraftCommand;
import io.github.sweetzonzi.py_port.common.item.AlgorithmAgentItem;
import io.github.sweetzonzi.py_port.common.item.CarItem;
import io.github.sweetzonzi.py_port.common.item.QuatUavItem;
import io.github.sweetzonzi.py_port.common.shop.ShopEventHandler;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(PyCraft.MOD_ID)
public class PyCraft {
    public static final String MOD_ID = "py_port";
    public static final Logger LOGGER = LoggerFactory.getLogger("PyCraft");

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    public static final DeferredItem<Item> UAV_ITEM = ITEMS.register("quat_uav", QuatUavItem::new);
    public static final DeferredItem<Item> CAR_ITEM = ITEMS.register("car_item", CarItem::new);
    public static final DeferredItem<Item> ALGORITHM_AI_ITEM = ITEMS.register("algorithm_ai", AlgorithmAgentItem::new);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> EXAMPLE_TAB = CREATIVE_MODE_TABS.register("example_tab", () -> CreativeModeTab.builder().title(Component.translatable("itemGroup.py_port")).withTabsBefore(CreativeModeTabs.COMBAT).icon(() -> UAV_ITEM.get().getDefaultInstance()).displayItems((parameters, output) -> {
        output.accept(UAV_ITEM.get());
        output.accept(CAR_ITEM.get());
        output.accept(ALGORITHM_AI_ITEM.get());
    }).build());

    public PyCraft(IEventBus modEventBus, ModContainer modContainer) {
        ITEMS.register(modEventBus);
        CREATIVE_MODE_TABS.register(modEventBus);

        NeoForge.EVENT_BUS.register(PyCraftCommand.class);
        NeoForge.EVENT_BUS.register(ShopEventHandler.class);
    }
}
