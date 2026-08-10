#!/usr/bin/with-contenv bash
# Routes only media-network traffic through the NAT gateway.
#
# Runs once at container start, before Chromium, via the LinuxServer
# custom-cont-init.d hook — which executes as root, the privilege `ip route`
# needs (granted by the container's NET_ADMIN capability).
#
# WHY A SCOPED ROUTE, NOT THE DEFAULT ROUTE. The browser must reach the media
# network (10.77.0.0/24: LiveKit, Coturn, the harness page) through nat-gw so
# its packets are source-NAT'd and a real server-reflexive candidate is formed.
# But the operator drives this browser over noVNC from the host, and that reply
# traffic must stay on the Docker bridge. Redirecting the DEFAULT route would
# send the noVNC replies to nat-gw as well and freeze the session. Adding a
# single route for the media subnet sends only media traffic across the NAT and
# leaves host access untouched.
#
# `replace` rather than `add` keeps a container restart idempotent: a second
# start must not fail on an existing route.

set -euo pipefail

MEDIA_SUBNET="10.77.0.0/24"
NAT_GATEWAY="10.88.0.2"

ip route replace "${MEDIA_SUBNET}" via "${NAT_GATEWAY}"

echo "media-route: ${MEDIA_SUBNET} now routed via nat-gw at ${NAT_GATEWAY}"
