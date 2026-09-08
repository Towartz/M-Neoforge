package baritone.api.behavior.look;

import baritone.api.utils.Rotation;

public interface IAimProcessor {
   Rotation peekRotation(Rotation var1);

   ITickableAimProcessor fork();
}
