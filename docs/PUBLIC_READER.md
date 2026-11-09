# Opt-in public reader candidate

Build the complete existing Portfolio frontend with `VITE_PUBLIC_SHOWCASE=true npm run build` to hide the admin footer and remove admin routes, and label operation demos as private access. Live/local demo links are omitted until public demos are approved. GitHub source and evidence may require access. With the flag unset, the private website keeps its existing management and demo entry points. No profile/personal information or CMS data changes are included.

The public build needs its own original Nginx gateway image. Keep the current private gateway unchanged. The host-level Caddy public allowlist must deny auth/admin/write requests and strip Cookie/Authorization; hiding frontend routes is not an access-control boundary. Do not expose the whole private gateway or any management/database/SMB/SSH/monitoring service.

This PR does not deploy anything or enable Tailscale Funnel/Cloudflare. Public ingress still requires a separate approval. The existing CI exercises the default private build and original full backend/container/browser/rollback/TLS contract; the added workflow checks the public typecheck/build and original Nginx image packaging.

Local evidence in server-management: original e94efa96 snapshot, only three UI files changed; public Vue typecheck/build succeeded, isolated native Caddy public boundary 34 checks and public reader desktop/mobile 16 navigations succeeded, with no admin/private-demo anchors. Existing production frontend, CMS, Caddy, Tailscale and personal-data decision remain unchanged.
