## Understanding How API Calls Work in Kubernetes

This section explains **how the UI communicates with backend services** when running in Kubernetes.

### The Challenge

When you run the application in Kubernetes (using Kind), there's a networking challenge:
- Your **browser** runs on your local computer (Windows)
- The **backend services** (data-service, search-service, etc.) run inside Kubernetes pods
- Browsers **cannot directly access** services running inside Kubernetes pods

Think of it like this: Your browser is in your house, but the backend services are in separate locked containers in a warehouse. You need a way to send messages between them.

### The Solution: Nginx Reverse Proxy

We use **nginx** as a "middleman" (reverse proxy) to bridge the gap between your browser and the backend services.

#### Flow Diagram

```
┌──────────────────────┐
│   Your Browser       │  You type: http://localhost:30000
│   (Local Machine)    │
└──────────┬───────────┘
           │
           │ (1) kubectl port-forward forwards traffic
           ▼
┌──────────────────────────────────────────────┐
│   UI Pod (Nginx Container)                   │
│   ┌────────────────────────────────────┐     │
│   │  Nginx Web Server                   │     │
│   │  • Serves HTML/CSS/JS files         │     │
│   │  • Proxies API requests             │     │
│   └────────────────────────────────────┘     │
│                                               │
│   When you request: /api/data/cases          │
│   Nginx forwards to: data-service:9090       │
└───────────────────┬───────────────────────────┘
                    │
                    │  (2) Kubernetes service networking
                    ▼
       ┌────────────────────────────┐
       │  data-service Pod          │
       │  Receives: /data/cases     │
       │  Processes & responds      │
       └────────────────────────────┘
```

### Nginx Configuration Explained

The nginx configuration file (`ui/nginx.conf`) contains special rules that tell nginx how to forward requests:

```nginx
location /api/data/ {
    proxy_pass http://data-service:9090/;
}
```

**What this means:**
1. When browser requests: `http://localhost:30000/api/data/cases`
2. Nginx sees the `/api/data/` prefix
3. Nginx removes `/api/data/` and forwards the rest (`/cases`) to `data-service:9090/`
4. Backend receives: `http://data-service:9090/data/cases`

### The Path Routing Fix

**Problem We Encountered:**

Initially, the nginx configuration looked like this:
```nginx
location /api/data/ {
    proxy_pass http://data-service:9090/data/;  # ❌ WRONG
}
```

This caused **double path segments**:
- Browser request: `/api/data/cases`
- Nginx forwarded to: `data-service:9090/data/data/cases` ❌
- Result: **404 Not Found** error

**The Fix:**

We removed the path suffix from `proxy_pass`:
```nginx
location /api/data/ {
    proxy_pass http://data-service:9090/;  # ✅ CORRECT
}
```

Now it works correctly:
- Browser request: `/api/data/cases`
- Nginx forwards to: `data-service:9090/data/cases` ✅
- Result: **Request succeeds!**

### Why Does This Work?

The Spring Boot controllers define their paths like this:
```java
@RestController
@RequestMapping("/data")  // Controller expects /data/cases
public class CaseController {
    @GetMapping("/cases")
    public List<Case> getCases() { ... }
}
```

So when nginx forwards to `data-service:9090/`, and the browser requested `/api/data/cases`,  
the controller receives `/data/cases` which matches `@RequestMapping("/data")` + `@GetMapping("/cases")`.

### All API Proxy Routes

Here are all the proxy rules configured in nginx:

| Browser Calls | Nginx Forwards To | Backend Controller Path |
|---------------|-------------------|------------------------|
| `/api/data/cases` | `data-service:9090/data/cases` | `@RequestMapping("/data")` |
| `/api/refdata/countries` | `refdata-service:9092/refdata/countries` | `@RequestMapping("/refdata")` |
| `/api/file/upload` | `file-service:9091/file/upload` | `@RequestMapping("/file")` |
| `/api/search/search/cases` | `search-service:9093/search/cases` | `@RequestMapping("/search")` |

### Key Takeaways

1. **Port-forward** connects your local machine to the UI pod
2. **Nginx** acts as a reverse proxy inside the UI pod
3. **Kubernetes service networking** allows pods to talk to each other using service names
4. **Path prefixes** (`/api/*`) help nginx route requests to the correct backend service
5. **Controller mappings** must match the paths that nginx forwards

This architecture allows a browser-based UI to communicate with multiple microservices running in Kubernetes!
