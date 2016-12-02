
/* Workarounds libvnc issues - listen.c edition
 * - Library calling fork() is unacceptable
 * - Fork cannot be supported on graphical Android applications
 * - libvnc is not portable to support platforms with no fork (even though autoconf will detect this)
 * - NDK does not support legacy wait3
 *
 * As a workaround, implement mock implementations
 */

#include "rfbclient.h"

void listenForIncomingConnections(rfbClient* client)
{
	return;
}

int listenForIncomingConnectionsNoFork(rfbClient* client, int timeout)
{
  return -1;
}
