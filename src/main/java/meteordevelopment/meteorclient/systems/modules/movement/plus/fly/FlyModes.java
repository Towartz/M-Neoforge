package meteordevelopment.meteorclient.systems.modules.movement.plus.fly;

public enum FlyModes {
    Vulcan_Clip,
    Matrix_Exploit_2,
    Matrix_Exploit,
    Damage,
    Damage_OldFag;

    @Override
    public String toString() {
        return name().replace('_', ' ');
    }
}
