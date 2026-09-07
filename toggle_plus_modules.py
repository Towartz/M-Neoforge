import os
import sys
import shutil

ROOT = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(ROOT, "src", "main", "java", "meteordevelopment", "meteorclient")

INTEGRATION_FILES = [
    os.path.join(SRC, "systems", "modules", "Categories.java"),
    os.path.join(SRC, "systems", "modules", "Modules.java"),
    os.path.join(SRC, "systems", "hud", "Hud.java"),
    os.path.join(SRC, "mixin", "ClientPlayerEntityMixin.java")
]

PLUS_DIRS = [
    os.path.join(SRC, "systems", "modules", "combat", "plus"),
    os.path.join(SRC, "systems", "modules", "movement", "plus"),
    os.path.join(SRC, "systems", "modules", "world", "plus"),
    os.path.join(SRC, "systems", "modules", "render", "plus"),
    os.path.join(SRC, "systems", "modules", "player", "plus"),
    os.path.join(SRC, "systems", "modules", "misc", "plus"),
    os.path.join(SRC, "utils", "plus")
]

INDIVIDUAL_FILES = [
    os.path.join(SRC, "systems", "hud", "elements", "TimerPlusCharge.java"),
    os.path.join(SRC, "events", "entity", "player", "PlayerUseMultiplierEvent.java")
]

def enable():
    print("Enabling Meteor+ modules...")
    count = 0
    # Enable individual files
    for f in INDIVIDUAL_FILES:
        bak = f + ".bak"
        if os.path.exists(bak):
            shutil.copy2(bak, f)
            count += 1
            print("Enabled: " + os.path.relpath(f, ROOT))
    
    # Enable dirs
    for d in PLUS_DIRS:
        if os.path.isdir(d):
            for root, _, files in os.walk(d):
                for file in files:
                    if file.endswith(".java.bak"):
                        bak_path = os.path.join(root, file)
                        java_path = bak_path[:-4]  # strip .bak
                        shutil.copy2(bak_path, java_path)
                        count += 1
                        print("Enabled: " + os.path.relpath(java_path, ROOT))

    # Restore plus integration files
    for f in INTEGRATION_FILES:
        bak = f + ".bak"
        if os.path.exists(bak):
            shutil.copy2(bak, f)
            print("Restored integration file: " + os.path.relpath(f, ROOT))

    print(f"Done. Enabled {count} modules.")

def disable():
    print("Disabling Meteor+ modules (converting to .bak)...")
    count = 0
    # Backup integration files
    for f in INTEGRATION_FILES:
        if os.path.exists(f):
            bak = f + ".bak"
            shutil.copy2(f, bak)
    
    # Convert individual files
    for f in INDIVIDUAL_FILES:
        bak = f + ".bak"
        if os.path.exists(bak):
            shutil.copy2(f, bak)
            if os.path.exists(f):
                os.remove(f)
            count += 1
            print("Disabled: " + os.path.relpath(f, ROOT))

    # Convert dirs
    for d in PLUS_DIRS:
        if os.path.isdir(d):
            for root, _, files in os.walk(d):
                for file in files:
                    if file.endswith(".java"):
                        java_path = os.path.join(root, file)
                        bak_path = java_path + ".bak"
                        shutil.copy2(java_path, bak_path)
                        os.remove(java_path)
                        count += 1
                        print("Disabled: " + os.path.relpath(java_path, ROOT))

    print(f"Done. Disabled {count} modules.")

if __name__ == '__main__':
    action = sys.argv[1].lower() if len(sys.argv) > 1 else 'status'
    if action == 'enable':
        enable()
    elif action == 'disable':
        disable()
    else:
        print("Usage: python toggle_plus_modules.py [enable|disable]")
