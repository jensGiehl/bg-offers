package de.agiehl.bgoffers.service;

import org.springframework.stereotype.Component;

@Component
public class DryRunContext {

    private final InheritableThreadLocal<Integer> depth = new InheritableThreadLocal<>();

    public void run(Runnable action) {
        var previousDepth = depth.get();
        depth.set(previousDepth == null ? 1 : previousDepth + 1);
        try {
            action.run();
        } finally {
            if (previousDepth == null) {
                depth.remove();
            } else {
                depth.set(previousDepth);
            }
        }
    }

    public boolean active() {
        return depth.get() != null;
    }
}
