package meteordevelopment.meteorclient.systems.modules.movement.plus.jesus;

public enum JesusModes {
    Matrix_Zoom,
    Matrix_Zoom_2,
    Vulcan_Exploit,
    NCP;

    @Override
    public String toString() {
        return name().replace('_', ' ');
    }
}
