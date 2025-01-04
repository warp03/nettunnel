/*
 * Copyright (C) 2023-2024 Wilton Arthur Poth
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0.
 * If a copy of the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.warp03.nettunnel;

import java.io.IOException;

class NTunException(msg: String, cause: Throwable) extends IOException(msg, cause) {

    def this(msg: String) = this(msg, null);
}
