package com.example.bodeul.ui.manager;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ManagerQualificationCompletionDialogGateTest {
    @Test
    public void backgroundCompletionThenResume_enqueuesDialogOnlyOnce() {
        ManagerQualificationCompletionDialogGate gate =
                new ManagerQualificationCompletionDialogGate();

        assertTrue(gate.tryEnqueue(true, false));
        assertFalse(gate.tryEnqueue(true, false));
    }

    @Test
    public void confirmedDialog_canBeEnqueuedForNextCompletion() {
        ManagerQualificationCompletionDialogGate gate =
                new ManagerQualificationCompletionDialogGate();

        assertTrue(gate.tryEnqueue(true, false));
        gate.clear();

        assertTrue(gate.tryEnqueue(true, false));
    }

    @Test
    public void restoredDialog_marksRequestAsAlreadyEnqueued() {
        ManagerQualificationCompletionDialogGate gate =
                new ManagerQualificationCompletionDialogGate();

        assertFalse(gate.tryEnqueue(true, true));
        assertFalse(gate.tryEnqueue(true, false));
    }
}
