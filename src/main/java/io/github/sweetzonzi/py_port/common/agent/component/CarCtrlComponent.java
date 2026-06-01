package io.github.sweetzonzi.py_port.common.agent.component;

import com.jme3.math.Vector3f;
import io.github.sweetzonzi.py_port.common.agent.CarEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CarCtrlComponent extends AbstractAgentComponent {
    // 控制输入
    private float throttle = 0f;      // 油门 -1到1
    private float steering = 0f;      // 转向 -1到1
    private boolean braking = false;   // 刹车
    private boolean handbrake = false; // 手刹

    // 巡线参数
    private float maxSpeed = 0.5f;       // 速度上限
    private float forwardForce = 150f;    // 前进推力
    private float brakeForce = 70f;      // 刹车力
    private float maxYawRate = 2f;     // 最大偏航角速度

    public CarCtrlComponent(String name, CarEntity car) {
        super(name, car);
    }

    @Override
    public void prePhysicsTick() {
        CarEntity car = (CarEntity) agent;

        if (handbrake) {
            car.setLinearVelocity(new Vector3f(0, 0, 0));
            car.setAngularVelocity(new Vector3f(0, 0, 0));
            return;
        }

        if (braking) {
            applyBrake(car);
            return;
        }

        car.getBody().activate();

        // 1. 前进：恒定推力 + 速度硬上限
        Vector3f forward = car.getFrontVector();
        car.getBody().applyCentralForce(forward.mult(throttle * forwardForce));

        // 速度超过 maxSpeed 就裁切
        Vector3f vel = car.getLinearVelocity();
        float speedSq = vel.lengthSquared();
        if (speedSq > maxSpeed * maxSpeed) {
            float scale = maxSpeed / (float) Math.sqrt(speedSq);
            vel.multLocal(scale);
            car.setLinearVelocity(vel);
        }

        // 2. 转向：直接设置偏航角速度
        car.setAngularVelocity(new Vector3f(0, steering * maxYawRate, 0));
    }

    private void applyBrake(CarEntity car) {
        Vector3f vel = car.getLinearVelocity();
        car.getBody().applyCentralForce(vel.mult(-brakeForce));
        // 刹车时立即停转
        car.setAngularVelocity(new Vector3f(0, 0, 0));
    }

    //  Python 调用接口
    public void setControl(float throttle, float steering, boolean brake) {
        this.throttle = Math.max(-1, Math.min(1, throttle));
        this.steering = Math.max(-1, Math.min(1, steering));
        this.braking = brake;
    }

    public void handbrake() {
        this.handbrake = true;
    }

    public void releaseHandbrake() {
        this.handbrake = false;
    }
}