## D1: Approach for trajectory divergence

**Choice:** Add output→mediating feedback loops to the CAPS topology
**Alternatives:**
- More extreme input activations — won't work; damping factor dominates convergence rate regardless of input magnitude
- Accept trajectory as topology property — abandons a psychologically meaningful signal that CAPS theory expects
**Rationale:** The current topology is feedforward with zero feedback loops. CAPS theory (Mischel & Shoda 1995) predicts personality-dependent convergence dynamics via recurrent connections. Adding tagged feedback connections creates genuine structural basis for trajectory divergence.
**Trade-offs:** Structural topology change affects all settling; needs careful weight calibration to avoid destabilising convergence
**Sources:** CapsSettler.java (damping at line 99), caps-topology.yaml (no output→mediating connections), Mischel & Shoda 1995, Gray 1982
**Exploration:** quick
**Status:** captured
