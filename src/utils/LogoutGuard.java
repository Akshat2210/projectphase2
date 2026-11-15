package utils;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Keeps logout unavailable while another application window is showing. */
public final class LogoutGuard {
    private final JFrame screen;
    private final JButton button;
    private final AWTEventListener listener;
    private boolean closed;

    public LogoutGuard(JFrame screen, JButton button) {
        this.screen = screen;
        this.button = button;
        listener = event -> {
            if (event.getSource() instanceof Window) {
                // Recheck after a Back action has finished hiding/disposing and restoring parents.
                SwingUtilities.invokeLater(this::refresh);
            }
        };
        Toolkit.getDefaultToolkit().addAWTEventListener(listener,
                AWTEvent.WINDOW_EVENT_MASK | AWTEvent.COMPONENT_EVENT_MASK);
        screen.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                closed = true;
                Toolkit.getDefaultToolkit().removeAWTEventListener(listener);
            }
        });
        refresh();
    }

    /** Recheck on the logout action too, rather than trusting a queued button refresh. */
    public boolean canLogout() {
        boolean allowed = !closed && !hasOtherShowingWindow(screen, Window.getWindows());
        button.setEnabled(allowed);
        button.setToolTipText(allowed ? null : "Close other application windows before logging out.");
        return allowed;
    }

    private void refresh() {
        if (!closed) canLogout();
    }

    private static boolean hasOtherShowingWindow(Window screen, Window[] windows) {
        for (Window window : windows) {
            // Older Back flows hide inactive frames instead of disposing them.
            // An active child still blocks logout even while its parent is hidden.
            if (window != screen && window.isShowing()) return true;
        }
        return false;
    }
}
