package com.enn3developer.becom.server;

import com.enn3developer.becom.logic.net.NodeKind;

/** A machine that is a node of a BeCom network; the network checks its saved nodes against these. */
public interface NodeHost {

    NodeKind nodeKind();
}
