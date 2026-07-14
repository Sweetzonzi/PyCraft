package io.github.sweetzonzi.py_port.common.agent;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 扫描结果：记录目标方块的坐标、状态和采集价值权重。
 */
public record ScanResult(BlockPos pos, BlockState state, int value) {
}