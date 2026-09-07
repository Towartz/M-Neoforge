package meteordevelopment.meteorclient.events.entity.player;

public class PlayerUseMultiplierEvent {
    private float forward = 0.2F;
    private float sideways = 0.2F;

    public PlayerUseMultiplierEvent(float forward, float sideways) {
        this.forward = forward;
        this.sideways = sideways;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public float getForward() {
        return this.forward;
    }

    public void setSideways(float sideways) {
        this.sideways = sideways;
    }

    public float getSideways() {
        return this.sideways;
    }
}
