package baritone.api.behavior.look;

import baritone.api.utils.Rotation;

public interface ITickableAimProcessor extends IAimProcessor {
   void tick();

   void advance(int var1);

   Rotation nextRotation(Rotation var1);
}
