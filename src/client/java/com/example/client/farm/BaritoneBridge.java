package com.example.client.farm;

import net.minecraft.core.BlockPos;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

public final class BaritoneBridge {
    private BaritoneBridge() {
    }

    // Baritone API 버전 차이를 흡수하기 위해 리플렉션으로 farm 프로세스를 호출한다.
    public static boolean startFarm(int range) {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object farmProcess = baritone.getClass().getMethod("getFarmProcess").invoke(baritone);

            for (Method method : farmProcess.getClass().getMethods()) {
                if (!method.getName().equals("farm")) continue;

                Class<?>[] params = method.getParameterTypes();

                if (params.length == 1 && params[0] == int.class) {
                    method.invoke(farmProcess, range);
                    return true;
                }

                if (params.length == 2 && params[0] == int.class) {
                    method.invoke(farmProcess, range, null);
                    return true;
                }
            }

            System.out.println("[FarmTest] No suitable farm(int...) method found");
            return false;

        } catch (Exception e) {
            System.out.println("[FarmTest] Baritone farm start failed");
            e.printStackTrace();
            return false;
        }
    }

    // 상자 좌표 근처로만 이동시키고 실제 상호작용은 모드 상태머신이 담당한다.
    public static boolean startGoalNear(BlockPos pos, int range) {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object goalProcess = baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);

            Class<?> goalClass = Class.forName("baritone.api.pathing.goals.Goal");
            Class<?> goalNearClass = Class.forName("baritone.api.pathing.goals.GoalNear");

            Object goal;

            try {
                Constructor<?> c = goalNearClass.getConstructor(BlockPos.class, int.class);
                goal = c.newInstance(pos, range);
            } catch (NoSuchMethodException e) {
                Constructor<?> c = goalNearClass.getConstructor(int.class, int.class, int.class, int.class);
                goal = c.newInstance(pos.getX(), pos.getY(), pos.getZ(), range);
            }

            Method setGoalAndPath = goalProcess.getClass().getMethod("setGoalAndPath", goalClass);
            setGoalAndPath.invoke(goalProcess, goal);

            return true;

        } catch (Exception e) {
            System.out.println("[FarmTest] Baritone start failed");
            e.printStackTrace();
            return false;
        }
    }

    // 상태 전환 시 이전 경로를 즉시 끊어서 중복 이동을 방지한다.
    public static void cancel() {
        try {
            Class<?> apiClass = Class.forName("baritone.api.BaritoneAPI");
            Object provider = apiClass.getMethod("getProvider").invoke(null);
            Object baritone = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
            Object pathingBehavior = baritone.getClass().getMethod("getPathingBehavior").invoke(baritone);

            pathingBehavior.getClass().getMethod("cancelEverything").invoke(pathingBehavior);

        } catch (Exception e) {
            System.out.println("[FarmTest] Baritone cancel failed");
            e.printStackTrace();
        }
    }
}
