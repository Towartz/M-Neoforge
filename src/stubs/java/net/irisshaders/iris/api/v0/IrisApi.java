package net.irisshaders.iris.api.v0;
public class IrisApi {
    private static final IrisApi INSTANCE = new IrisApi();
    public static IrisApi getInstance() { return INSTANCE; }
    public boolean isShaderPackInUse() { return false; }
}
