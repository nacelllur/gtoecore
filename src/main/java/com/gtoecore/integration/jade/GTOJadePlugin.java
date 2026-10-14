package com.gtoecore.integration.jade;

import com.gtoecore.gt.CombustionEngineMachine;
import com.gtoecore.gt.GTOEnergyConverterMachine;
import com.gtoecore.gt.GTOEnergyInputHatchPartMachine;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * GTOE Jade 集成 —— 机器/仓的实时读数（EU 缓存、输出值、能量仓七种能量、引擎燃烧状态）。
 *
 * <p>服务端 provider 把数据写进 accessor 的 serverData（NBT），客户端 provider 读 NBT 渲染，
 * 不直接碰客户端侧的机器实例（Jade 的跨端约定）。</p>
 */
@WailaPlugin
public class GTOJadePlugin implements IWailaPlugin {

    public static final GTOEnergyProvider ENERGY_PROVIDER = new GTOEnergyProvider();

    @Override
    public void register(IWailaCommonRegistration registration) {
        // 服务端：把机器/仓的读数打包进 NBT（只对 GTM 机器方块生效，普通方块自动跳过）
        registration.registerBlockDataProvider(ENERGY_PROVIDER, BlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // 客户端：对 GTM 机器方块（MetaMachineBlock 子类）渲染
        registration.registerBlockComponent(ENERGY_PROVIDER, Block.class);
    }
}
