package com.example.dario_dell.smartphone;

import java.util.ArrayList;
import java.util.List;

public class MathUtils {

    /**
     * 1-D Trapezoidal Rule For Cumulative Integration with unit spacing
     *
     * @param signal The input
     * @return
     */
    public static List<Float> cumtrapz(List<Float> signal) {
        int n = signal.size();
        List<Float> cumtrap = new ArrayList<>();

        cumtrap.add(0.0f);
        for (int i = 1; i < n - 1; i++) {
            cumtrap.add(cumtrap.get(i - 1) + 0.5f * (signal.get(i) + signal.get(i - 1)));
        }
        cumtrap.add(cumtrap.get(n - 2) + 0.5f * (signal.get(n - 1) + signal.get(n - 2)));
        return cumtrap;
    }
}
