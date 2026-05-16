package io.github.sweetzonzi.py_port.util.control;

import io.github.sweetzonzi.py_port.PyCraft;
import lombok.Getter;

/**
 * 一个简单的PID控制器，用于实现对各个可动部件的控制
 * <p>
 * 若不需要积分项请考虑直接使用PD控制器，更加节约内存
 *
 * @author 甜粽子
 */
@Getter
public class PIDController {
    private double P;
    private double I;
    private double D;
    private double error;//实时误差
    private double errorLastFrame;//前一次迭代的误差
    private double errorAccumulated;//累积误差
    private double outputMax;//累积误差上限
    private double outputMin;//累积误差下限
    private double errorSpeed;//误差变化率
    private double lastOutput;//上一次输出
    private double STEP;//PID控制器的运行步长

    /**
     * 创造一个PID控制器，能够根据被控量的误差给出动态的控制量
     *
     * @param p    比例系数：误差越大，控制量越大
     * @param i    积分系数：误差累积量越大，控制量越大
     * @param d    微分系数：误差变化速度越大，控制量越大
     * @param step 控制器运行步长，通常与物理计算步长相同
     * @param min  输出的最小值，用于防止积分饱和
     * @param max  输出的最大值，用于防止积分饱和
     */
    public PIDController(double p, double i, double d, double step, double min, double max) {
        this.adjust(p, i, d, step, min, max);
    }

    /**
     * 调整PID控制器的参数
     *
     * @param p    比例系数：误差越大，控制量越大
     * @param i    积分系数：误差累积量越大，控制量越大
     * @param d    微分系数：误差变化速度越大，控制量越大
     * @param step 控制器运行步长，通常与物理计算步长相同
     * @param outputMin  输出的最小值，用于防止积分饱和
     * @param outputMax  输出的最大值，用于防止积分饱和
     */
    public void adjust(double p, double i, double d, double step, double outputMin, double outputMax) {
        this.P = p;
        this.I = i;
        this.D = d;
        this.outputMax = outputMax;
        this.outputMin = outputMin;
        if (step <= 0) {
            this.STEP = 0.1;
            PyCraft.LOGGER.error("PID's time step must be greater than 0!");
        } else {
            this.STEP = step;
        }
        this.resetError();
    }

    /**
     * 重设此PID控制器的记录误差，使其不会影响到新的控制量计算
     */
    public void resetError() {
        this.error = 0;
        this.errorAccumulated = 0;
        this.errorSpeed = 0;
        this.errorLastFrame = 0;
        this.lastOutput = 0.5 * (this.outputMax + this.outputMin);
    }

    /**
     * 对于给定的被控量，控制器会根据和目标的差距给出一个控制量以尝试修正被控量的值
     * <p>
     * 此方法应随时间推进反复被调用
     *
     * @param target 被控量目标值，例如目标速度或目标炮塔旋转角度
     * @param actual 被控量实际值，例如实际速度或实际炮塔旋转角度
     * @return 控制量输出，例如推力或炮塔旋转力矩的大小
     */
    public double step(double target, double actual) {
        this.error = target - actual;//更新记录的误差
        this.errorSpeed = (this.error - this.errorLastFrame) / this.STEP;
        return internalStep();
    }

    /**
     * 对于给定的被控量，控制器会根据和目标的差距给出一个控制量以尝试修正被控量的值
     * <p>
     * 此方法应随时间推进反复被调用
     *
     * @param target 被控量目标值，例如目标速度或目标炮塔旋转角度
     * @param actual 被控量实际值，例如实际速度或实际炮塔旋转角度
     * @param speed  误差变化率，不根据记录误差计算，而是直接传入
     * @return 控制量输出，例如推力或炮塔旋转力矩的大小
     */
    public double step(double target, double actual, double speed) {
        this.error = target - actual;//更新记录的误差
        this.errorSpeed = speed;
        return internalStep();
    }

    private double internalStep() {
        if (lastOutput < outputMax && lastOutput > outputMin)
            this.errorAccumulated = this.errorAccumulated + this.error * this.STEP;
        //P
        double output = this.P * error;
        //I
        output += this.I * errorAccumulated;
        //D
        output += this.D * errorSpeed;
        this.errorLastFrame = this.error;//更新记录的误差
        lastOutput = output;
        return Math.clamp(output, outputMin, outputMax);
    }
}
