package meteordevelopment.meteorclient.systems.modules.movement.plus.speed;

public enum SpeedModes {
    NCP_Hop,
    Matrix_Exploit,
    Matrix_Exploit_2,
    Matrix_6dot7dot0,
    Matrix,
    Vulcan,
    Vulcan_2dot8dot6,
    AAC_Hop_4dot3dot8;

    @Override
    public String toString() {
        return super.toString().replace('_', ' ').replaceAll("dot", ".");
    }
}
