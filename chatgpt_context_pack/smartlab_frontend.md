# smartlab_frontend

Generated at: 2026-07-03T11:09:11

This file is generated from the local SmartLab repository for model-readable project context.

## File Tree

- Frontend/index.html
- Frontend/package-lock.json
- Frontend/package.json
- Frontend/src/App.vue
- Frontend/src/components/device/DeviceCategorySidebar.vue
- Frontend/src/components/layout/AppTopNav.vue
- Frontend/src/components/layouts/MainLayout.vue
- Frontend/src/main.js
- Frontend/src/router/index.js
- Frontend/src/stores/authStore.ts
- Frontend/src/style.css
- Frontend/src/utils/request.js
- Frontend/src/views/admin/UserManagement.vue
- Frontend/src/views/auth/Login.vue
- Frontend/src/views/auth/Register.vue
- Frontend/src/views/dashboard/Dashboard.vue
- Frontend/src/views/data/DataManagement.vue
- Frontend/src/views/device/AdapterManagement.vue
- Frontend/src/views/device/DeviceInstanceManagement.vue
- Frontend/src/views/device/DeviceModelManagement.vue
- Frontend/src/views/security/SecurityCenter.vue
- Frontend/src/views/task/TaskList.vue
- Frontend/src/views/task/WorkflowDesigner.vue
- Frontend/vite.config.js

## Files

---

## Frontend/index.html

````text
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>SmartLab</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.js"></script>
  </body>
</html>

````

---

## Frontend/package-lock.json

````text
{
  "name": "smartlab-front",
  "version": "0.0.0",
  "lockfileVersion": 3,
  "requires": true,
  "packages": {
    "": {
      "name": "smartlab-front",
      "version": "0.0.0",
      "dependencies": {
        "@vue-flow/background": "^1.3.2",
        "@vue-flow/controls": "^1.1.3",
        "@vue-flow/core": "^1.48.2",
        "axios": "^1.13.6",
        "echarts": "^6.1.0",
        "element-plus": "^2.13.5",
        "pinia": "^3.0.4",
        "vue": "^3.5.13",
        "vue-router": "^5.0.3"
      },
      "devDependencies": {
        "@vitejs/plugin-vue": "^5.2.1",
        "vite": "^6.0.5"
      }
    },
    "node_modules/@babel/generator": {
      "version": "7.29.1",
      "resolved": "https://registry.npmmirror.com/@babel/generator/-/generator-7.29.1.tgz",
      "integrity": "sha512-qsaF+9Qcm2Qv8SRIMMscAvG4O3lJ0F1GuMo5HR/Bp02LopNgnZBC/EkbevHFeGs4ls/oPz9v+Bsmzbkbe+0dUw==",
      "license": "MIT",
      "dependencies": {
        "@babel/parser": "^7.29.0",
        "@babel/types": "^7.29.0",
        "@jridgewell/gen-mapping": "^0.3.12",
        "@jridgewell/trace-mapping": "^0.3.28",
        "jsesc": "^3.0.2"
      },
      "engines": {
        "node": ">=6.9.0"
      }
    },
    "node_modules/@babel/helper-string-parser": {
      "version": "7.27.1",
      "resolved": "https://registry.npmmirror.com/@babel/helper-string-parser/-/helper-string-parser-7.27.1.tgz",
      "integrity": "sha512-qMlSxKbpRlAridDExk92nSobyDdpPijUq2DW6oDnUqd0iOGxmQjyqhMIihI9+zv4LPyZdRje2cavWPbCbWm3eA==",
      "license": "MIT",
      "engines": {
        "node": ">=6.9.0"
      }
    },
    "node_modules/@babel/helper-validator-identifier": {
      "version": "7.28.5",
      "resolved": "https://registry.npmmirror.com/@babel/helper-validator-identifier/-/helper-validator-identifier-7.28.5.tgz",
      "integrity": "sha512-qSs4ifwzKJSV39ucNjsvc6WVHs6b7S03sOh2OcHF9UHfVPqWWALUsNUVzhSBiItjRZoLHx7nIarVjqKVusUZ1Q==",
      "license": "MIT",
      "engines": {
        "node": ">=6.9.0"
      }
    },
    "node_modules/@babel/parser": {
      "version": "7.29.0",
      "resolved": "https://registry.npmmirror.com/@babel/parser/-/parser-7.29.0.tgz",
      "integrity": "sha512-IyDgFV5GeDUVX4YdF/3CPULtVGSXXMLh1xVIgdCgxApktqnQV0r7/8Nqthg+8YLGaAtdyIlo2qIdZrbCv4+7ww==",
      "license": "MIT",
      "dependencies": {
        "@babel/types": "^7.29.0"
      },
      "bin": {
        "parser": "bin/babel-parser.js"
      },
      "engines": {
        "node": ">=6.0.0"
      }
    },
    "node_modules/@babel/types": {
      "version": "7.29.0",
      "resolved": "https://registry.npmmirror.com/@babel/types/-/types-7.29.0.tgz",
      "integrity": "sha512-LwdZHpScM4Qz8Xw2iKSzS+cfglZzJGvofQICy7W7v4caru4EaAmyUuO6BGrbyQ2mYV11W0U8j5mBhd14dd3B0A==",
      "license": "MIT",
      "dependencies": {
        "@babel/helper-string-parser": "^7.27.1",
        "@babel/helper-validator-identifier": "^7.28.5"
      },
      "engines": {
        "node": ">=6.9.0"
      }
    },
    "node_modules/@ctrl/tinycolor": {
      "version": "4.2.0",
      "resolved": "https://registry.npmmirror.com/@ctrl/tinycolor/-/tinycolor-4.2.0.tgz",
      "integrity": "sha512-kzyuwOAQnXJNLS9PSyrk0CWk35nWJW/zl/6KvnTBMFK65gm7U1/Z5BqjxeapjZCIhQcM/DsrEmcbRwDyXyXK4A==",
      "license": "MIT",
      "engines": {
        "node": ">=14"
      }
    },
    "node_modules/@element-plus/icons-vue": {
      "version": "2.3.2",
      "resolved": "https://registry.npmmirror.com/@element-plus/icons-vue/-/icons-vue-2.3.2.tgz",
      "integrity": "sha512-OzIuTaIfC8QXEPmJvB4Y4kw34rSXdCJzxcD1kFStBvr8bK6X1zQAYDo0CNMjojnfTqRQCJ0I7prlErcoRiET2A==",
      "license": "MIT",
      "peerDependencies": {
        "vue": "^3.2.0"
      }
    },
    "node_modules/@esbuild/aix-ppc64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/aix-ppc64/-/aix-ppc64-0.25.12.tgz",
      "integrity": "sha512-Hhmwd6CInZ3dwpuGTF8fJG6yoWmsToE+vYgD4nytZVxcu1ulHpUQRAB1UJ8+N1Am3Mz4+xOByoQoSZf4D+CpkA==",
      "cpu": [
        "ppc64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "aix"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/android-arm": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/android-arm/-/android-arm-0.25.12.tgz",
      "integrity": "sha512-VJ+sKvNA/GE7Ccacc9Cha7bpS8nyzVv0jdVgwNDaR4gDMC/2TTRc33Ip8qrNYUcpkOHUT5OZ0bUcNNVZQ9RLlg==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/android-arm64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/android-arm64/-/android-arm64-0.25.12.tgz",
      "integrity": "sha512-6AAmLG7zwD1Z159jCKPvAxZd4y/VTO0VkprYy+3N2FtJ8+BQWFXU+OxARIwA46c5tdD9SsKGZ/1ocqBS/gAKHg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/android-x64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/android-x64/-/android-x64-0.25.12.tgz",
      "integrity": "sha512-5jbb+2hhDHx5phYR2By8GTWEzn6I9UqR11Kwf22iKbNpYrsmRB18aX/9ivc5cabcUiAT/wM+YIZ6SG9QO6a8kg==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/darwin-arm64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/darwin-arm64/-/darwin-arm64-0.25.12.tgz",
      "integrity": "sha512-N3zl+lxHCifgIlcMUP5016ESkeQjLj/959RxxNYIthIg+CQHInujFuXeWbWMgnTo4cp5XVHqFPmpyu9J65C1Yg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/darwin-x64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/darwin-x64/-/darwin-x64-0.25.12.tgz",
      "integrity": "sha512-HQ9ka4Kx21qHXwtlTUVbKJOAnmG1ipXhdWTmNXiPzPfWKpXqASVcWdnf2bnL73wgjNrFXAa3yYvBSd9pzfEIpA==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/freebsd-arm64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/freebsd-arm64/-/freebsd-arm64-0.25.12.tgz",
      "integrity": "sha512-gA0Bx759+7Jve03K1S0vkOu5Lg/85dou3EseOGUes8flVOGxbhDDh/iZaoek11Y8mtyKPGF3vP8XhnkDEAmzeg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "freebsd"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/freebsd-x64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/freebsd-x64/-/freebsd-x64-0.25.12.tgz",
      "integrity": "sha512-TGbO26Yw2xsHzxtbVFGEXBFH0FRAP7gtcPE7P5yP7wGy7cXK2oO7RyOhL5NLiqTlBh47XhmIUXuGciXEqYFfBQ==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "freebsd"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-arm": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-arm/-/linux-arm-0.25.12.tgz",
      "integrity": "sha512-lPDGyC1JPDou8kGcywY0YILzWlhhnRjdof3UlcoqYmS9El818LLfJJc3PXXgZHrHCAKs/Z2SeZtDJr5MrkxtOw==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-arm64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-arm64/-/linux-arm64-0.25.12.tgz",
      "integrity": "sha512-8bwX7a8FghIgrupcxb4aUmYDLp8pX06rGh5HqDT7bB+8Rdells6mHvrFHHW2JAOPZUbnjUpKTLg6ECyzvas2AQ==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-ia32": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-ia32/-/linux-ia32-0.25.12.tgz",
      "integrity": "sha512-0y9KrdVnbMM2/vG8KfU0byhUN+EFCny9+8g202gYqSSVMonbsCfLjUO+rCci7pM0WBEtz+oK/PIwHkzxkyharA==",
      "cpu": [
        "ia32"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-loong64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-loong64/-/linux-loong64-0.25.12.tgz",
      "integrity": "sha512-h///Lr5a9rib/v1GGqXVGzjL4TMvVTv+s1DPoxQdz7l/AYv6LDSxdIwzxkrPW438oUXiDtwM10o9PmwS/6Z0Ng==",
      "cpu": [
        "loong64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-mips64el": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-mips64el/-/linux-mips64el-0.25.12.tgz",
      "integrity": "sha512-iyRrM1Pzy9GFMDLsXn1iHUm18nhKnNMWscjmp4+hpafcZjrr2WbT//d20xaGljXDBYHqRcl8HnxbX6uaA/eGVw==",
      "cpu": [
        "mips64el"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-ppc64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-ppc64/-/linux-ppc64-0.25.12.tgz",
      "integrity": "sha512-9meM/lRXxMi5PSUqEXRCtVjEZBGwB7P/D4yT8UG/mwIdze2aV4Vo6U5gD3+RsoHXKkHCfSxZKzmDssVlRj1QQA==",
      "cpu": [
        "ppc64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-riscv64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-riscv64/-/linux-riscv64-0.25.12.tgz",
      "integrity": "sha512-Zr7KR4hgKUpWAwb1f3o5ygT04MzqVrGEGXGLnj15YQDJErYu/BGg+wmFlIDOdJp0PmB0lLvxFIOXZgFRrdjR0w==",
      "cpu": [
        "riscv64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-s390x": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-s390x/-/linux-s390x-0.25.12.tgz",
      "integrity": "sha512-MsKncOcgTNvdtiISc/jZs/Zf8d0cl/t3gYWX8J9ubBnVOwlk65UIEEvgBORTiljloIWnBzLs4qhzPkJcitIzIg==",
      "cpu": [
        "s390x"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/linux-x64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/linux-x64/-/linux-x64-0.25.12.tgz",
      "integrity": "sha512-uqZMTLr/zR/ed4jIGnwSLkaHmPjOjJvnm6TVVitAa08SLS9Z0VM8wIRx7gWbJB5/J54YuIMInDquWyYvQLZkgw==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/netbsd-arm64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/netbsd-arm64/-/netbsd-arm64-0.25.12.tgz",
      "integrity": "sha512-xXwcTq4GhRM7J9A8Gv5boanHhRa/Q9KLVmcyXHCTaM4wKfIpWkdXiMog/KsnxzJ0A1+nD+zoecuzqPmCRyBGjg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "netbsd"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/netbsd-x64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/netbsd-x64/-/netbsd-x64-0.25.12.tgz",
      "integrity": "sha512-Ld5pTlzPy3YwGec4OuHh1aCVCRvOXdH8DgRjfDy/oumVovmuSzWfnSJg+VtakB9Cm0gxNO9BzWkj6mtO1FMXkQ==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "netbsd"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/openbsd-arm64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/openbsd-arm64/-/openbsd-arm64-0.25.12.tgz",
      "integrity": "sha512-fF96T6KsBo/pkQI950FARU9apGNTSlZGsv1jZBAlcLL1MLjLNIWPBkj5NlSz8aAzYKg+eNqknrUJ24QBybeR5A==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "openbsd"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/openbsd-x64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/openbsd-x64/-/openbsd-x64-0.25.12.tgz",
      "integrity": "sha512-MZyXUkZHjQxUvzK7rN8DJ3SRmrVrke8ZyRusHlP+kuwqTcfWLyqMOE3sScPPyeIXN/mDJIfGXvcMqCgYKekoQw==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "openbsd"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/openharmony-arm64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/openharmony-arm64/-/openharmony-arm64-0.25.12.tgz",
      "integrity": "sha512-rm0YWsqUSRrjncSXGA7Zv78Nbnw4XL6/dzr20cyrQf7ZmRcsovpcRBdhD43Nuk3y7XIoW2OxMVvwuRvk9XdASg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "openharmony"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/sunos-x64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/sunos-x64/-/sunos-x64-0.25.12.tgz",
      "integrity": "sha512-3wGSCDyuTHQUzt0nV7bocDy72r2lI33QL3gkDNGkod22EsYl04sMf0qLb8luNKTOmgF/eDEDP5BFNwoBKH441w==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "sunos"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/win32-arm64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/win32-arm64/-/win32-arm64-0.25.12.tgz",
      "integrity": "sha512-rMmLrur64A7+DKlnSuwqUdRKyd3UE7oPJZmnljqEptesKM8wx9J8gx5u0+9Pq0fQQW8vqeKebwNXdfOyP+8Bsg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/win32-ia32": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/win32-ia32/-/win32-ia32-0.25.12.tgz",
      "integrity": "sha512-HkqnmmBoCbCwxUKKNPBixiWDGCpQGVsrQfJoVGYLPT41XWF8lHuE5N6WhVia2n4o5QK5M4tYr21827fNhi4byQ==",
      "cpu": [
        "ia32"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@esbuild/win32-x64": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/@esbuild/win32-x64/-/win32-x64-0.25.12.tgz",
      "integrity": "sha512-alJC0uCZpTFrSL0CCDjcgleBXPnCrEAhTBILpeAp7M/OFgoqtAetfBzX0xM00MUsVVPpVjlPuMbREqnZCXaTnA==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ],
      "engines": {
        "node": ">=18"
      }
    },
    "node_modules/@floating-ui/core": {
      "version": "1.7.5",
      "resolved": "https://registry.npmmirror.com/@floating-ui/core/-/core-1.7.5.tgz",
      "integrity": "sha512-1Ih4WTWyw0+lKyFMcBHGbb5U5FtuHJuujoyyr5zTaWS5EYMeT6Jb2AuDeftsCsEuchO+mM2ij5+q9crhydzLhQ==",
      "license": "MIT",
      "dependencies": {
        "@floating-ui/utils": "^0.2.11"
      }
    },
    "node_modules/@floating-ui/dom": {
      "version": "1.7.6",
      "resolved": "https://registry.npmmirror.com/@floating-ui/dom/-/dom-1.7.6.tgz",
      "integrity": "sha512-9gZSAI5XM36880PPMm//9dfiEngYoC6Am2izES1FF406YFsjvyBMmeJ2g4SAju3xWwtuynNRFL2s9hgxpLI5SQ==",
      "license": "MIT",
      "dependencies": {
        "@floating-ui/core": "^1.7.5",
        "@floating-ui/utils": "^0.2.11"
      }
    },
    "node_modules/@floating-ui/utils": {
      "version": "0.2.11",
      "resolved": "https://registry.npmmirror.com/@floating-ui/utils/-/utils-0.2.11.tgz",
      "integrity": "sha512-RiB/yIh78pcIxl6lLMG0CgBXAZ2Y0eVHqMPYugu+9U0AeT6YBeiJpf7lbdJNIugFP5SIjwNRgo4DhR1Qxi26Gg==",
      "license": "MIT"
    },
    "node_modules/@jridgewell/gen-mapping": {
      "version": "0.3.13",
      "resolved": "https://registry.npmmirror.com/@jridgewell/gen-mapping/-/gen-mapping-0.3.13.tgz",
      "integrity": "sha512-2kkt/7niJ6MgEPxF0bYdQ6etZaA+fQvDcLKckhy1yIQOzaoKjBBjSj63/aLVjYE3qhRt5dvM+uUyfCg6UKCBbA==",
      "license": "MIT",
      "dependencies": {
        "@jridgewell/sourcemap-codec": "^1.5.0",
        "@jridgewell/trace-mapping": "^0.3.24"
      }
    },
    "node_modules/@jridgewell/remapping": {
      "version": "2.3.5",
      "resolved": "https://registry.npmmirror.com/@jridgewell/remapping/-/remapping-2.3.5.tgz",
      "integrity": "sha512-LI9u/+laYG4Ds1TDKSJW2YPrIlcVYOwi2fUC6xB43lueCjgxV4lffOCZCtYFiH6TNOX+tQKXx97T4IKHbhyHEQ==",
      "license": "MIT",
      "dependencies": {
        "@jridgewell/gen-mapping": "^0.3.5",
        "@jridgewell/trace-mapping": "^0.3.24"
      }
    },
    "node_modules/@jridgewell/resolve-uri": {
      "version": "3.1.2",
      "resolved": "https://registry.npmmirror.com/@jridgewell/resolve-uri/-/resolve-uri-3.1.2.tgz",
      "integrity": "sha512-bRISgCIjP20/tbWSPWMEi54QVPRZExkuD9lJL+UIxUKtwVJA8wW1Trb1jMs1RFXo1CBTNZ/5hpC9QvmKWdopKw==",
      "license": "MIT",
      "engines": {
        "node": ">=6.0.0"
      }
    },
    "node_modules/@jridgewell/sourcemap-codec": {
      "version": "1.5.5",
      "resolved": "https://registry.npmmirror.com/@jridgewell/sourcemap-codec/-/sourcemap-codec-1.5.5.tgz",
      "integrity": "sha512-cYQ9310grqxueWbl+WuIUIaiUaDcj7WOq5fVhEljNVgRfOUhY9fy2zTvfoqWsnebh8Sl70VScFbICvJnLKB0Og==",
      "license": "MIT"
    },
    "node_modules/@jridgewell/trace-mapping": {
      "version": "0.3.31",
      "resolved": "https://registry.npmmirror.com/@jridgewell/trace-mapping/-/trace-mapping-0.3.31.tgz",
      "integrity": "sha512-zzNR+SdQSDJzc8joaeP8QQoCQr8NuYx2dIIytl1QeBEZHJ9uW6hebsrYgbz8hJwUQao3TWCMtmfV8Nu1twOLAw==",
      "license": "MIT",
      "dependencies": {
        "@jridgewell/resolve-uri": "^3.1.0",
        "@jridgewell/sourcemap-codec": "^1.4.14"
      }
    },
    "node_modules/@popperjs/core": {
      "name": "@sxzz/popperjs-es",
      "version": "2.11.8",
      "resolved": "https://registry.npmmirror.com/@sxzz/popperjs-es/-/popperjs-es-2.11.8.tgz",
      "integrity": "sha512-wOwESXvvED3S8xBmcPWHs2dUuzrE4XiZeFu7e1hROIJkm02a49N120pmOXxY33sBb6hArItm5W5tcg1cBtV+HQ==",
      "license": "MIT",
      "funding": {
        "type": "opencollective",
        "url": "https://opencollective.com/popperjs"
      }
    },
    "node_modules/@rollup/rollup-android-arm-eabi": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-android-arm-eabi/-/rollup-android-arm-eabi-4.59.0.tgz",
      "integrity": "sha512-upnNBkA6ZH2VKGcBj9Fyl9IGNPULcjXRlg0LLeaioQWueH30p6IXtJEbKAgvyv+mJaMxSm1l6xwDXYjpEMiLMg==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ]
    },
    "node_modules/@rollup/rollup-android-arm64": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-android-arm64/-/rollup-android-arm64-4.59.0.tgz",
      "integrity": "sha512-hZ+Zxj3SySm4A/DylsDKZAeVg0mvi++0PYVceVyX7hemkw7OreKdCvW2oQ3T1FMZvCaQXqOTHb8qmBShoqk69Q==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "android"
      ]
    },
    "node_modules/@rollup/rollup-darwin-arm64": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-darwin-arm64/-/rollup-darwin-arm64-4.59.0.tgz",
      "integrity": "sha512-W2Psnbh1J8ZJw0xKAd8zdNgF9HRLkdWwwdWqubSVk0pUuQkoHnv7rx4GiF9rT4t5DIZGAsConRE3AxCdJ4m8rg==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ]
    },
    "node_modules/@rollup/rollup-darwin-x64": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-darwin-x64/-/rollup-darwin-x64-4.59.0.tgz",
      "integrity": "sha512-ZW2KkwlS4lwTv7ZVsYDiARfFCnSGhzYPdiOU4IM2fDbL+QGlyAbjgSFuqNRbSthybLbIJ915UtZBtmuLrQAT/w==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ]
    },
    "node_modules/@rollup/rollup-freebsd-arm64": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-freebsd-arm64/-/rollup-freebsd-arm64-4.59.0.tgz",
      "integrity": "sha512-EsKaJ5ytAu9jI3lonzn3BgG8iRBjV4LxZexygcQbpiU0wU0ATxhNVEpXKfUa0pS05gTcSDMKpn3Sx+QB9RlTTA==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "freebsd"
      ]
    },
    "node_modules/@rollup/rollup-freebsd-x64": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-freebsd-x64/-/rollup-freebsd-x64-4.59.0.tgz",
      "integrity": "sha512-d3DuZi2KzTMjImrxoHIAODUZYoUUMsuUiY4SRRcJy6NJoZ6iIqWnJu9IScV9jXysyGMVuW+KNzZvBLOcpdl3Vg==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "freebsd"
      ]
    },
    "node_modules/@rollup/rollup-linux-arm-gnueabihf": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-arm-gnueabihf/-/rollup-linux-arm-gnueabihf-4.59.0.tgz",
      "integrity": "sha512-t4ONHboXi/3E0rT6OZl1pKbl2Vgxf9vJfWgmUoCEVQVxhW6Cw/c8I6hbbu7DAvgp82RKiH7TpLwxnJeKv2pbsw==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-arm-musleabihf": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-arm-musleabihf/-/rollup-linux-arm-musleabihf-4.59.0.tgz",
      "integrity": "sha512-CikFT7aYPA2ufMD086cVORBYGHffBo4K8MQ4uPS/ZnY54GKj36i196u8U+aDVT2LX4eSMbyHtyOh7D7Zvk2VvA==",
      "cpu": [
        "arm"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-arm64-gnu": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-arm64-gnu/-/rollup-linux-arm64-gnu-4.59.0.tgz",
      "integrity": "sha512-jYgUGk5aLd1nUb1CtQ8E+t5JhLc9x5WdBKew9ZgAXg7DBk0ZHErLHdXM24rfX+bKrFe+Xp5YuJo54I5HFjGDAA==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-arm64-musl": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-arm64-musl/-/rollup-linux-arm64-musl-4.59.0.tgz",
      "integrity": "sha512-peZRVEdnFWZ5Bh2KeumKG9ty7aCXzzEsHShOZEFiCQlDEepP1dpUl/SrUNXNg13UmZl+gzVDPsiCwnV1uI0RUA==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-loong64-gnu": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-loong64-gnu/-/rollup-linux-loong64-gnu-4.59.0.tgz",
      "integrity": "sha512-gbUSW/97f7+r4gHy3Jlup8zDG190AuodsWnNiXErp9mT90iCy9NKKU0Xwx5k8VlRAIV2uU9CsMnEFg/xXaOfXg==",
      "cpu": [
        "loong64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-loong64-musl": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-loong64-musl/-/rollup-linux-loong64-musl-4.59.0.tgz",
      "integrity": "sha512-yTRONe79E+o0FWFijasoTjtzG9EBedFXJMl888NBEDCDV9I2wGbFFfJQQe63OijbFCUZqxpHz1GzpbtSFikJ4Q==",
      "cpu": [
        "loong64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-ppc64-gnu": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-ppc64-gnu/-/rollup-linux-ppc64-gnu-4.59.0.tgz",
      "integrity": "sha512-sw1o3tfyk12k3OEpRddF68a1unZ5VCN7zoTNtSn2KndUE+ea3m3ROOKRCZxEpmT9nsGnogpFP9x6mnLTCaoLkA==",
      "cpu": [
        "ppc64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-ppc64-musl": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-ppc64-musl/-/rollup-linux-ppc64-musl-4.59.0.tgz",
      "integrity": "sha512-+2kLtQ4xT3AiIxkzFVFXfsmlZiG5FXYW7ZyIIvGA7Bdeuh9Z0aN4hVyXS/G1E9bTP/vqszNIN/pUKCk/BTHsKA==",
      "cpu": [
        "ppc64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-riscv64-gnu": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-riscv64-gnu/-/rollup-linux-riscv64-gnu-4.59.0.tgz",
      "integrity": "sha512-NDYMpsXYJJaj+I7UdwIuHHNxXZ/b/N2hR15NyH3m2qAtb/hHPA4g4SuuvrdxetTdndfj9b1WOmy73kcPRoERUg==",
      "cpu": [
        "riscv64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-riscv64-musl": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-riscv64-musl/-/rollup-linux-riscv64-musl-4.59.0.tgz",
      "integrity": "sha512-nLckB8WOqHIf1bhymk+oHxvM9D3tyPndZH8i8+35p/1YiVoVswPid2yLzgX7ZJP0KQvnkhM4H6QZ5m0LzbyIAg==",
      "cpu": [
        "riscv64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-s390x-gnu": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-s390x-gnu/-/rollup-linux-s390x-gnu-4.59.0.tgz",
      "integrity": "sha512-oF87Ie3uAIvORFBpwnCvUzdeYUqi2wY6jRFWJAy1qus/udHFYIkplYRW+wo+GRUP4sKzYdmE1Y3+rY5Gc4ZO+w==",
      "cpu": [
        "s390x"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-x64-gnu": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-x64-gnu/-/rollup-linux-x64-gnu-4.59.0.tgz",
      "integrity": "sha512-3AHmtQq/ppNuUspKAlvA8HtLybkDflkMuLK4DPo77DfthRb71V84/c4MlWJXixZz4uruIH4uaa07IqoAkG64fg==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-linux-x64-musl": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-linux-x64-musl/-/rollup-linux-x64-musl-4.59.0.tgz",
      "integrity": "sha512-2UdiwS/9cTAx7qIUZB/fWtToJwvt0Vbo0zmnYt7ED35KPg13Q0ym1g442THLC7VyI6JfYTP4PiSOWyoMdV2/xg==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "linux"
      ]
    },
    "node_modules/@rollup/rollup-openbsd-x64": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-openbsd-x64/-/rollup-openbsd-x64-4.59.0.tgz",
      "integrity": "sha512-M3bLRAVk6GOwFlPTIxVBSYKUaqfLrn8l0psKinkCFxl4lQvOSz8ZrKDz2gxcBwHFpci0B6rttydI4IpS4IS/jQ==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "openbsd"
      ]
    },
    "node_modules/@rollup/rollup-openharmony-arm64": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-openharmony-arm64/-/rollup-openharmony-arm64-4.59.0.tgz",
      "integrity": "sha512-tt9KBJqaqp5i5HUZzoafHZX8b5Q2Fe7UjYERADll83O4fGqJ49O1FsL6LpdzVFQcpwvnyd0i+K/VSwu/o/nWlA==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "openharmony"
      ]
    },
    "node_modules/@rollup/rollup-win32-arm64-msvc": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-win32-arm64-msvc/-/rollup-win32-arm64-msvc-4.59.0.tgz",
      "integrity": "sha512-V5B6mG7OrGTwnxaNUzZTDTjDS7F75PO1ae6MJYdiMu60sq0CqN5CVeVsbhPxalupvTX8gXVSU9gq+Rx1/hvu6A==",
      "cpu": [
        "arm64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ]
    },
    "node_modules/@rollup/rollup-win32-ia32-msvc": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-win32-ia32-msvc/-/rollup-win32-ia32-msvc-4.59.0.tgz",
      "integrity": "sha512-UKFMHPuM9R0iBegwzKF4y0C4J9u8C6MEJgFuXTBerMk7EJ92GFVFYBfOZaSGLu6COf7FxpQNqhNS4c4icUPqxA==",
      "cpu": [
        "ia32"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ]
    },
    "node_modules/@rollup/rollup-win32-x64-gnu": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-win32-x64-gnu/-/rollup-win32-x64-gnu-4.59.0.tgz",
      "integrity": "sha512-laBkYlSS1n2L8fSo1thDNGrCTQMmxjYY5G0WFWjFFYZkKPjsMBsgJfGf4TLxXrF6RyhI60L8TMOjBMvXiTcxeA==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ]
    },
    "node_modules/@rollup/rollup-win32-x64-msvc": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/@rollup/rollup-win32-x64-msvc/-/rollup-win32-x64-msvc-4.59.0.tgz",
      "integrity": "sha512-2HRCml6OztYXyJXAvdDXPKcawukWY2GpR5/nxKp4iBgiO3wcoEGkAaqctIbZcNB6KlUQBIqt8VYkNSj2397EfA==",
      "cpu": [
        "x64"
      ],
      "dev": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "win32"
      ]
    },
    "node_modules/@types/estree": {
      "version": "1.0.8",
      "resolved": "https://registry.npmmirror.com/@types/estree/-/estree-1.0.8.tgz",
      "integrity": "sha512-dWHzHa2WqEXI/O1E9OjrocMTKJl2mSrEolh1Iomrv6U+JuNwaHXsXx9bLu5gG7BUWFIN0skIQJQ/L1rIex4X6w==",
      "dev": true,
      "license": "MIT"
    },
    "node_modules/@types/lodash": {
      "version": "4.17.24",
      "resolved": "https://registry.npmmirror.com/@types/lodash/-/lodash-4.17.24.tgz",
      "integrity": "sha512-gIW7lQLZbue7lRSWEFql49QJJWThrTFFeIMJdp3eH4tKoxm1OvEPg02rm4wCCSHS0cL3/Fizimb35b7k8atwsQ==",
      "license": "MIT"
    },
    "node_modules/@types/lodash-es": {
      "version": "4.17.12",
      "resolved": "https://registry.npmmirror.com/@types/lodash-es/-/lodash-es-4.17.12.tgz",
      "integrity": "sha512-0NgftHUcV4v34VhXm8QBSftKVXtbkBG3ViCjs6+eJ5a6y6Mi/jiFGPc1sC7QK+9BFhWrURE3EOggmWaSxL9OzQ==",
      "license": "MIT",
      "dependencies": {
        "@types/lodash": "*"
      }
    },
    "node_modules/@types/web-bluetooth": {
      "version": "0.0.20",
      "resolved": "https://registry.npmmirror.com/@types/web-bluetooth/-/web-bluetooth-0.0.20.tgz",
      "integrity": "sha512-g9gZnnXVq7gM7v3tJCWV/qw7w+KeOlSHAhgF9RytFyifW6AF61hdT2ucrYhPq9hLs5JIryeupHV3qGk95dH9ow==",
      "license": "MIT"
    },
    "node_modules/@vitejs/plugin-vue": {
      "version": "5.2.4",
      "resolved": "https://registry.npmmirror.com/@vitejs/plugin-vue/-/plugin-vue-5.2.4.tgz",
      "integrity": "sha512-7Yx/SXSOcQq5HiiV3orevHUFn+pmMB4cgbEkDYgnkUWb0WfeQ/wa2yFv6D5ICiCQOVpjA7vYDXrC7AGO8yjDHA==",
      "dev": true,
      "license": "MIT",
      "engines": {
        "node": "^18.0.0 || >=20.0.0"
      },
      "peerDependencies": {
        "vite": "^5.0.0 || ^6.0.0",
        "vue": "^3.2.25"
      }
    },
    "node_modules/@vue-flow/background": {
      "version": "1.3.2",
      "resolved": "https://registry.npmmirror.com/@vue-flow/background/-/background-1.3.2.tgz",
      "integrity": "sha512-eJPhDcLj1wEo45bBoqTXw1uhl0yK2RaQGnEINqvvBsAFKh/camHJd5NPmOdS1w+M9lggc9igUewxaEd3iCQX2w==",
      "license": "MIT",
      "peerDependencies": {
        "@vue-flow/core": "^1.23.0",
        "vue": "^3.3.0"
      }
    },
    "node_modules/@vue-flow/controls": {
      "version": "1.1.3",
      "resolved": "https://registry.npmmirror.com/@vue-flow/controls/-/controls-1.1.3.tgz",
      "integrity": "sha512-XCf+G+jCvaWURdFlZmOjifZGw3XMhN5hHlfMGkWh9xot+9nH9gdTZtn+ldIJKtarg3B21iyHU8JjKDhYcB6JMw==",
      "license": "MIT",
      "peerDependencies": {
        "@vue-flow/core": "^1.23.0",
        "vue": "^3.3.0"
      }
    },
    "node_modules/@vue-flow/core": {
      "version": "1.48.2",
      "resolved": "https://registry.npmmirror.com/@vue-flow/core/-/core-1.48.2.tgz",
      "integrity": "sha512-raxhgKWE+G/mcEvXJjGFUDYW9rAI3GOtiHR3ZkNpwBWuIaCC1EYiBmKGwJOoNzVFgwO7COgErnK7i08i287AFA==",
      "license": "MIT",
      "dependencies": {
        "@vueuse/core": "^10.5.0",
        "d3-drag": "^3.0.0",
        "d3-interpolate": "^3.0.1",
        "d3-selection": "^3.0.0",
        "d3-zoom": "^3.0.0"
      },
      "peerDependencies": {
        "vue": "^3.3.0"
      }
    },
    "node_modules/@vue-flow/core/node_modules/@vueuse/core": {
      "version": "10.11.1",
      "resolved": "https://registry.npmmirror.com/@vueuse/core/-/core-10.11.1.tgz",
      "integrity": "sha512-guoy26JQktXPcz+0n3GukWIy/JDNKti9v6VEMu6kV2sYBsWuGiTU8OWdg+ADfUbHg3/3DlqySDe7JmdHrktiww==",
      "license": "MIT",
      "dependencies": {
        "@types/web-bluetooth": "^0.0.20",
        "@vueuse/metadata": "10.11.1",
        "@vueuse/shared": "10.11.1",
        "vue-demi": ">=0.14.8"
      },
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      }
    },
    "node_modules/@vue-flow/core/node_modules/@vueuse/core/node_modules/vue-demi": {
      "version": "0.14.10",
      "resolved": "https://registry.npmmirror.com/vue-demi/-/vue-demi-0.14.10.tgz",
      "integrity": "sha512-nMZBOwuzabUO0nLgIcc6rycZEebF6eeUfaiQx9+WSk8e29IbLvPU9feI6tqW4kTo3hvoYAJkMh8n8D0fuISphg==",
      "hasInstallScript": true,
      "license": "MIT",
      "bin": {
        "vue-demi-fix": "bin/vue-demi-fix.js",
        "vue-demi-switch": "bin/vue-demi-switch.js"
      },
      "engines": {
        "node": ">=12"
      },
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      },
      "peerDependencies": {
        "@vue/composition-api": "^1.0.0-rc.1",
        "vue": "^3.0.0-0 || ^2.6.0"
      },
      "peerDependenciesMeta": {
        "@vue/composition-api": {
          "optional": true
        }
      }
    },
    "node_modules/@vue-flow/core/node_modules/@vueuse/metadata": {
      "version": "10.11.1",
      "resolved": "https://registry.npmmirror.com/@vueuse/metadata/-/metadata-10.11.1.tgz",
      "integrity": "sha512-IGa5FXd003Ug1qAZmyE8wF3sJ81xGLSqTqtQ6jaVfkeZ4i5kS2mwQF61yhVqojRnenVew5PldLyRgvdl4YYuSw==",
      "license": "MIT",
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      }
    },
    "node_modules/@vue-flow/core/node_modules/@vueuse/shared": {
      "version": "10.11.1",
      "resolved": "https://registry.npmmirror.com/@vueuse/shared/-/shared-10.11.1.tgz",
      "integrity": "sha512-LHpC8711VFZlDaYUXEBbFBCQ7GS3dVU9mjOhhMhXP6txTV4EhYQg/KGnQuvt/sPAtoUKq7VVUnL6mVtFoL42sA==",
      "license": "MIT",
      "dependencies": {
        "vue-demi": ">=0.14.8"
      },
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      }
    },
    "node_modules/@vue-flow/core/node_modules/@vueuse/shared/node_modules/vue-demi": {
      "version": "0.14.10",
      "resolved": "https://registry.npmmirror.com/vue-demi/-/vue-demi-0.14.10.tgz",
      "integrity": "sha512-nMZBOwuzabUO0nLgIcc6rycZEebF6eeUfaiQx9+WSk8e29IbLvPU9feI6tqW4kTo3hvoYAJkMh8n8D0fuISphg==",
      "hasInstallScript": true,
      "license": "MIT",
      "bin": {
        "vue-demi-fix": "bin/vue-demi-fix.js",
        "vue-demi-switch": "bin/vue-demi-switch.js"
      },
      "engines": {
        "node": ">=12"
      },
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      },
      "peerDependencies": {
        "@vue/composition-api": "^1.0.0-rc.1",
        "vue": "^3.0.0-0 || ^2.6.0"
      },
      "peerDependenciesMeta": {
        "@vue/composition-api": {
          "optional": true
        }
      }
    },
    "node_modules/@vue-macros/common": {
      "version": "3.1.2",
      "resolved": "https://registry.npmmirror.com/@vue-macros/common/-/common-3.1.2.tgz",
      "integrity": "sha512-h9t4ArDdniO9ekYHAD95t9AZcAbb19lEGK+26iAjUODOIJKmObDNBSe4+6ELQAA3vtYiFPPBtHh7+cQCKi3Dng==",
      "license": "MIT",
      "dependencies": {
        "@vue/compiler-sfc": "^3.5.22",
        "ast-kit": "^2.1.2",
        "local-pkg": "^1.1.2",
        "magic-string-ast": "^1.0.2",
        "unplugin-utils": "^0.3.0"
      },
      "engines": {
        "node": ">=20.19.0"
      },
      "funding": {
        "url": "https://github.com/sponsors/vue-macros"
      },
      "peerDependencies": {
        "vue": "^2.7.0 || ^3.2.25"
      },
      "peerDependenciesMeta": {
        "vue": {
          "optional": true
        }
      }
    },
    "node_modules/@vue/compiler-core": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/compiler-core/-/compiler-core-3.5.30.tgz",
      "integrity": "sha512-s3DfdZkcu/qExZ+td75015ljzHc6vE+30cFMGRPROYjqkroYI5NV2X1yAMX9UeyBNWB9MxCfPcsjpLS11nzkkw==",
      "license": "MIT",
      "dependencies": {
        "@babel/parser": "^7.29.0",
        "@vue/shared": "3.5.30",
        "entities": "^7.0.1",
        "estree-walker": "^2.0.2",
        "source-map-js": "^1.2.1"
      }
    },
    "node_modules/@vue/compiler-dom": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/compiler-dom/-/compiler-dom-3.5.30.tgz",
      "integrity": "sha512-eCFYESUEVYHhiMuK4SQTldO3RYxyMR/UQL4KdGD1Yrkfdx4m/HYuZ9jSfPdA+nWJY34VWndiYdW/wZXyiPEB9g==",
      "license": "MIT",
      "dependencies": {
        "@vue/compiler-core": "3.5.30",
        "@vue/shared": "3.5.30"
      }
    },
    "node_modules/@vue/compiler-sfc": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/compiler-sfc/-/compiler-sfc-3.5.30.tgz",
      "integrity": "sha512-LqmFPDn89dtU9vI3wHJnwaV6GfTRD87AjWpTWpyrdVOObVtjIuSeZr181z5C4PmVx/V3j2p+0f7edFKGRMpQ5A==",
      "license": "MIT",
      "dependencies": {
        "@babel/parser": "^7.29.0",
        "@vue/compiler-core": "3.5.30",
        "@vue/compiler-dom": "3.5.30",
        "@vue/compiler-ssr": "3.5.30",
        "@vue/shared": "3.5.30",
        "estree-walker": "^2.0.2",
        "magic-string": "^0.30.21",
        "postcss": "^8.5.8",
        "source-map-js": "^1.2.1"
      }
    },
    "node_modules/@vue/compiler-ssr": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/compiler-ssr/-/compiler-ssr-3.5.30.tgz",
      "integrity": "sha512-NsYK6OMTnx109PSL2IAyf62JP6EUdk4Dmj6AkWcJGBvN0dQoMYtVekAmdqgTtWQgEJo+Okstbf/1p7qZr5H+bA==",
      "license": "MIT",
      "dependencies": {
        "@vue/compiler-dom": "3.5.30",
        "@vue/shared": "3.5.30"
      }
    },
    "node_modules/@vue/devtools-api": {
      "version": "7.7.9",
      "resolved": "https://registry.npmmirror.com/@vue/devtools-api/-/devtools-api-7.7.9.tgz",
      "integrity": "sha512-kIE8wvwlcZ6TJTbNeU2HQNtaxLx3a84aotTITUuL/4bzfPxzajGBOoqjMhwZJ8L9qFYDU/lAYMEEm11dnZOD6g==",
      "license": "MIT",
      "dependencies": {
        "@vue/devtools-kit": "^7.7.9"
      }
    },
    "node_modules/@vue/devtools-kit": {
      "version": "7.7.9",
      "resolved": "https://registry.npmmirror.com/@vue/devtools-kit/-/devtools-kit-7.7.9.tgz",
      "integrity": "sha512-PyQ6odHSgiDVd4hnTP+aDk2X4gl2HmLDfiyEnn3/oV+ckFDuswRs4IbBT7vacMuGdwY/XemxBoh302ctbsptuA==",
      "license": "MIT",
      "dependencies": {
        "@vue/devtools-shared": "^7.7.9",
        "birpc": "^2.3.0",
        "hookable": "^5.5.3",
        "mitt": "^3.0.1",
        "perfect-debounce": "^1.0.0",
        "speakingurl": "^14.0.1",
        "superjson": "^2.2.2"
      }
    },
    "node_modules/@vue/devtools-shared": {
      "version": "7.7.9",
      "resolved": "https://registry.npmmirror.com/@vue/devtools-shared/-/devtools-shared-7.7.9.tgz",
      "integrity": "sha512-iWAb0v2WYf0QWmxCGy0seZNDPdO3Sp5+u78ORnyeonS6MT4PC7VPrryX2BpMJrwlDeaZ6BD4vP4XKjK0SZqaeA==",
      "license": "MIT",
      "dependencies": {
        "rfdc": "^1.4.1"
      }
    },
    "node_modules/@vue/reactivity": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/reactivity/-/reactivity-3.5.30.tgz",
      "integrity": "sha512-179YNgKATuwj9gB+66snskRDOitDiuOZqkYia7mHKJaidOMo/WJxHKF8DuGc4V4XbYTJANlfEKb0yxTQotnx4Q==",
      "license": "MIT",
      "dependencies": {
        "@vue/shared": "3.5.30"
      }
    },
    "node_modules/@vue/runtime-core": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/runtime-core/-/runtime-core-3.5.30.tgz",
      "integrity": "sha512-e0Z+8PQsUTdwV8TtEsLzUM7SzC7lQwYKePydb7K2ZnmS6jjND+WJXkmmfh/swYzRyfP1EY3fpdesyYoymCzYfg==",
      "license": "MIT",
      "dependencies": {
        "@vue/reactivity": "3.5.30",
        "@vue/shared": "3.5.30"
      }
    },
    "node_modules/@vue/runtime-dom": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/runtime-dom/-/runtime-dom-3.5.30.tgz",
      "integrity": "sha512-2UIGakjU4WSQ0T4iwDEW0W7vQj6n7AFn7taqZ9Cvm0Q/RA2FFOziLESrDL4GmtI1wV3jXg5nMoJSYO66egDUBw==",
      "license": "MIT",
      "dependencies": {
        "@vue/reactivity": "3.5.30",
        "@vue/runtime-core": "3.5.30",
        "@vue/shared": "3.5.30",
        "csstype": "^3.2.3"
      }
    },
    "node_modules/@vue/server-renderer": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/server-renderer/-/server-renderer-3.5.30.tgz",
      "integrity": "sha512-v+R34icapydRwbZRD0sXwtHqrQJv38JuMB4JxbOxd8NEpGLny7cncMp53W9UH/zo4j8eDHjQ1dEJXwzFQknjtQ==",
      "license": "MIT",
      "dependencies": {
        "@vue/compiler-ssr": "3.5.30",
        "@vue/shared": "3.5.30"
      },
      "peerDependencies": {
        "vue": "3.5.30"
      }
    },
    "node_modules/@vue/shared": {
      "version": "3.5.30",
      "resolved": "https://registry.npmmirror.com/@vue/shared/-/shared-3.5.30.tgz",
      "integrity": "sha512-YXgQ7JjaO18NeK2K9VTbDHaFy62WrObMa6XERNfNOkAhD1F1oDSf3ZJ7K6GqabZ0BvSDHajp8qfS5Sa2I9n8uQ==",
      "license": "MIT"
    },
    "node_modules/@vueuse/core": {
      "version": "12.0.0",
      "resolved": "https://registry.npmmirror.com/@vueuse/core/-/core-12.0.0.tgz",
      "integrity": "sha512-C12RukhXiJCbx4MGhjmd/gH52TjJsc3G0E0kQj/kb19H3Nt6n1CA4DRWuTdWWcaFRdlTe0npWDS942mvacvNBw==",
      "license": "MIT",
      "dependencies": {
        "@types/web-bluetooth": "^0.0.20",
        "@vueuse/metadata": "12.0.0",
        "@vueuse/shared": "12.0.0",
        "vue": "^3.5.13"
      },
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      }
    },
    "node_modules/@vueuse/metadata": {
      "version": "12.0.0",
      "resolved": "https://registry.npmmirror.com/@vueuse/metadata/-/metadata-12.0.0.tgz",
      "integrity": "sha512-Yzimd1D3sjxTDOlF05HekU5aSGdKjxhuhRFHA7gDWLn57PRbBIh+SF5NmjhJ0WRgF3my7T8LBucyxdFJjIfRJQ==",
      "license": "MIT",
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      }
    },
    "node_modules/@vueuse/shared": {
      "version": "12.0.0",
      "resolved": "https://registry.npmmirror.com/@vueuse/shared/-/shared-12.0.0.tgz",
      "integrity": "sha512-3i6qtcq2PIio5i/vVYidkkcgvmTjCqrf26u+Fd4LhnbBmIT6FN8y6q/GJERp8lfcB9zVEfjdV0Br0443qZuJpw==",
      "license": "MIT",
      "dependencies": {
        "vue": "^3.5.13"
      },
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      }
    },
    "node_modules/acorn": {
      "version": "8.16.0",
      "resolved": "https://registry.npmmirror.com/acorn/-/acorn-8.16.0.tgz",
      "integrity": "sha512-UVJyE9MttOsBQIDKw1skb9nAwQuR5wuGD3+82K6JgJlm/Y+KI92oNsMNGZCYdDsVtRHSak0pcV5Dno5+4jh9sw==",
      "license": "MIT",
      "bin": {
        "acorn": "bin/acorn"
      },
      "engines": {
        "node": ">=0.4.0"
      }
    },
    "node_modules/ast-kit": {
      "version": "2.2.0",
      "resolved": "https://registry.npmmirror.com/ast-kit/-/ast-kit-2.2.0.tgz",
      "integrity": "sha512-m1Q/RaVOnTp9JxPX+F+Zn7IcLYMzM8kZofDImfsKZd8MbR+ikdOzTeztStWqfrqIxZnYWryyI9ePm3NGjnZgGw==",
      "license": "MIT",
      "dependencies": {
        "@babel/parser": "^7.28.5",
        "pathe": "^2.0.3"
      },
      "engines": {
        "node": ">=20.19.0"
      },
      "funding": {
        "url": "https://github.com/sponsors/sxzz"
      }
    },
    "node_modules/ast-walker-scope": {
      "version": "0.8.3",
      "resolved": "https://registry.npmmirror.com/ast-walker-scope/-/ast-walker-scope-0.8.3.tgz",
      "integrity": "sha512-cbdCP0PGOBq0ASG+sjnKIoYkWMKhhz+F/h9pRexUdX2Hd38+WOlBkRKlqkGOSm0YQpcFMQBJeK4WspUAkwsEdg==",
      "license": "MIT",
      "dependencies": {
        "@babel/parser": "^7.28.4",
        "ast-kit": "^2.1.3"
      },
      "engines": {
        "node": ">=20.19.0"
      },
      "funding": {
        "url": "https://github.com/sponsors/sxzz"
      }
    },
    "node_modules/async-validator": {
      "version": "4.2.5",
      "resolved": "https://registry.npmmirror.com/async-validator/-/async-validator-4.2.5.tgz",
      "integrity": "sha512-7HhHjtERjqlNbZtqNqy2rckN/SpOOlmDliet+lP7k+eKZEjPk3DgyeU9lIXLdeLz0uBbbVp+9Qdow9wJWgwwfg==",
      "license": "MIT"
    },
    "node_modules/asynckit": {
      "version": "0.4.0",
      "resolved": "https://registry.npmmirror.com/asynckit/-/asynckit-0.4.0.tgz",
      "integrity": "sha512-Oei9OH4tRh0YqU3GxhX79dM/mwVgvbZJaSNaRk+bshkj0S5cfHcgYakreBjrHwatXKbz+IoIdYLxrKim2MjW0Q==",
      "license": "MIT"
    },
    "node_modules/axios": {
      "version": "1.13.6",
      "resolved": "https://registry.npmmirror.com/axios/-/axios-1.13.6.tgz",
      "integrity": "sha512-ChTCHMouEe2kn713WHbQGcuYrr6fXTBiu460OTwWrWob16g1bXn4vtz07Ope7ewMozJAnEquLk5lWQWtBig9DQ==",
      "license": "MIT",
      "dependencies": {
        "follow-redirects": "^1.15.11",
        "form-data": "^4.0.5",
        "proxy-from-env": "^1.1.0"
      }
    },
    "node_modules/birpc": {
      "version": "2.9.0",
      "resolved": "https://registry.npmmirror.com/birpc/-/birpc-2.9.0.tgz",
      "integrity": "sha512-KrayHS5pBi69Xi9JmvoqrIgYGDkD6mcSe/i6YKi3w5kekCLzrX4+nawcXqrj2tIp50Kw/mT/s3p+GVK0A0sKxw==",
      "license": "MIT",
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      }
    },
    "node_modules/call-bind-apply-helpers": {
      "version": "1.0.2",
      "resolved": "https://registry.npmmirror.com/call-bind-apply-helpers/-/call-bind-apply-helpers-1.0.2.tgz",
      "integrity": "sha512-Sp1ablJ0ivDkSzjcaJdxEunN5/XvksFJ2sMBFfq6x0ryhQV/2b/KwFe21cMpmHtPOSij8K99/wSfoEuTObmuMQ==",
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0",
        "function-bind": "^1.1.2"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/chokidar": {
      "version": "5.0.0",
      "resolved": "https://registry.npmmirror.com/chokidar/-/chokidar-5.0.0.tgz",
      "integrity": "sha512-TQMmc3w+5AxjpL8iIiwebF73dRDF4fBIieAqGn9RGCWaEVwQ6Fb2cGe31Yns0RRIzii5goJ1Y7xbMwo1TxMplw==",
      "license": "MIT",
      "dependencies": {
        "readdirp": "^5.0.0"
      },
      "engines": {
        "node": ">= 20.19.0"
      },
      "funding": {
        "url": "https://paulmillr.com/funding/"
      }
    },
    "node_modules/combined-stream": {
      "version": "1.0.8",
      "resolved": "https://registry.npmmirror.com/combined-stream/-/combined-stream-1.0.8.tgz",
      "integrity": "sha512-FQN4MRfuJeHf7cBbBMJFXhKSDq+2kAArBlmRBvcvFE5BB1HZKXtSFASDhdlz9zOYwxh8lDdnvmMOe/+5cdoEdg==",
      "license": "MIT",
      "dependencies": {
        "delayed-stream": "~1.0.0"
      },
      "engines": {
        "node": ">= 0.8"
      }
    },
    "node_modules/confbox": {
      "version": "0.2.4",
      "resolved": "https://registry.npmmirror.com/confbox/-/confbox-0.2.4.tgz",
      "integrity": "sha512-ysOGlgTFbN2/Y6Cg3Iye8YKulHw+R2fNXHrgSmXISQdMnomY6eNDprVdW9R5xBguEqI954+S6709UyiO7B+6OQ==",
      "license": "MIT"
    },
    "node_modules/copy-anything": {
      "version": "4.0.5",
      "resolved": "https://registry.npmmirror.com/copy-anything/-/copy-anything-4.0.5.tgz",
      "integrity": "sha512-7Vv6asjS4gMOuILabD3l739tsaxFQmC+a7pLZm02zyvs8p977bL3zEgq3yDk5rn9B0PbYgIv++jmHcuUab4RhA==",
      "license": "MIT",
      "dependencies": {
        "is-what": "^5.2.0"
      },
      "engines": {
        "node": ">=18"
      },
      "funding": {
        "url": "https://github.com/sponsors/mesqueeb"
      }
    },
    "node_modules/csstype": {
      "version": "3.2.3",
      "resolved": "https://registry.npmmirror.com/csstype/-/csstype-3.2.3.tgz",
      "integrity": "sha512-z1HGKcYy2xA8AGQfwrn0PAy+PB7X/GSj3UVJW9qKyn43xWa+gl5nXmU4qqLMRzWVLFC8KusUX8T/0kCiOYpAIQ==",
      "license": "MIT"
    },
    "node_modules/d3-color": {
      "version": "3.1.0",
      "resolved": "https://registry.npmmirror.com/d3-color/-/d3-color-3.1.0.tgz",
      "integrity": "sha512-zg/chbXyeBtMQ1LbD/WSoW2DpC3I0mpmPdW+ynRTj/x2DAWYrIY7qeZIHidozwV24m4iavr15lNwIwLxRmOxhA==",
      "license": "ISC",
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/d3-dispatch": {
      "version": "3.0.1",
      "resolved": "https://registry.npmmirror.com/d3-dispatch/-/d3-dispatch-3.0.1.tgz",
      "integrity": "sha512-rzUyPU/S7rwUflMyLc1ETDeBj0NRuHKKAcvukozwhshr6g6c5d8zh4c2gQjY2bZ0dXeGLWc1PF174P2tVvKhfg==",
      "license": "ISC",
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/d3-drag": {
      "version": "3.0.0",
      "resolved": "https://registry.npmmirror.com/d3-drag/-/d3-drag-3.0.0.tgz",
      "integrity": "sha512-pWbUJLdETVA8lQNJecMxoXfH6x+mO2UQo8rSmZ+QqxcbyA3hfeprFgIT//HW2nlHChWeIIMwS2Fq+gEARkhTkg==",
      "license": "ISC",
      "dependencies": {
        "d3-dispatch": "1 - 3",
        "d3-selection": "3"
      },
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/d3-ease": {
      "version": "3.0.1",
      "resolved": "https://registry.npmmirror.com/d3-ease/-/d3-ease-3.0.1.tgz",
      "integrity": "sha512-wR/XK3D3XcLIZwpbvQwQ5fK+8Ykds1ip7A2Txe0yxncXSdq1L9skcG7blcedkOX+ZcgxGAmLX1FrRGbADwzi0w==",
      "license": "BSD-3-Clause",
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/d3-interpolate": {
      "version": "3.0.1",
      "resolved": "https://registry.npmmirror.com/d3-interpolate/-/d3-interpolate-3.0.1.tgz",
      "integrity": "sha512-3bYs1rOD33uo8aqJfKP3JWPAibgw8Zm2+L9vBKEHJ2Rg+viTR7o5Mmv5mZcieN+FRYaAOWX5SJATX6k1PWz72g==",
      "license": "ISC",
      "dependencies": {
        "d3-color": "1 - 3"
      },
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/d3-selection": {
      "version": "3.0.0",
      "resolved": "https://registry.npmmirror.com/d3-selection/-/d3-selection-3.0.0.tgz",
      "integrity": "sha512-fmTRWbNMmsmWq6xJV8D19U/gw/bwrHfNXxrIN+HfZgnzqTHp9jOmKMhsTUjXOJnZOdZY9Q28y4yebKzqDKlxlQ==",
      "license": "ISC",
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/d3-timer": {
      "version": "3.0.1",
      "resolved": "https://registry.npmmirror.com/d3-timer/-/d3-timer-3.0.1.tgz",
      "integrity": "sha512-ndfJ/JxxMd3nw31uyKoY2naivF+r29V+Lc0svZxe1JvvIRmi8hUsrMvdOwgS1o6uBHmiz91geQ0ylPP0aj1VUA==",
      "license": "ISC",
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/d3-transition": {
      "version": "3.0.1",
      "resolved": "https://registry.npmmirror.com/d3-transition/-/d3-transition-3.0.1.tgz",
      "integrity": "sha512-ApKvfjsSR6tg06xrL434C0WydLr7JewBB3V+/39RMHsaXTOG0zmt/OAXeng5M5LBm0ojmxJrpomQVZ1aPvBL4w==",
      "license": "ISC",
      "dependencies": {
        "d3-color": "1 - 3",
        "d3-dispatch": "1 - 3",
        "d3-ease": "1 - 3",
        "d3-interpolate": "1 - 3",
        "d3-timer": "1 - 3"
      },
      "engines": {
        "node": ">=12"
      },
      "peerDependencies": {
        "d3-selection": "2 - 3"
      }
    },
    "node_modules/d3-zoom": {
      "version": "3.0.0",
      "resolved": "https://registry.npmmirror.com/d3-zoom/-/d3-zoom-3.0.0.tgz",
      "integrity": "sha512-b8AmV3kfQaqWAuacbPuNbL6vahnOJflOhexLzMMNLga62+/nh0JzvJ0aO/5a5MVgUFGS7Hu1P9P03o3fJkDCyw==",
      "license": "ISC",
      "dependencies": {
        "d3-dispatch": "1 - 3",
        "d3-drag": "2 - 3",
        "d3-interpolate": "1 - 3",
        "d3-selection": "2 - 3",
        "d3-transition": "2 - 3"
      },
      "engines": {
        "node": ">=12"
      }
    },
    "node_modules/dayjs": {
      "version": "1.11.20",
      "resolved": "https://registry.npmmirror.com/dayjs/-/dayjs-1.11.20.tgz",
      "integrity": "sha512-YbwwqR/uYpeoP4pu043q+LTDLFBLApUP6VxRihdfNTqu4ubqMlGDLd6ErXhEgsyvY0K6nCs7nggYumAN+9uEuQ==",
      "license": "MIT"
    },
    "node_modules/delayed-stream": {
      "version": "1.0.0",
      "resolved": "https://registry.npmmirror.com/delayed-stream/-/delayed-stream-1.0.0.tgz",
      "integrity": "sha512-ZySD7Nf91aLB0RxL4KGrKHBXl7Eds1DAmEdcoVawXnLD7SDhpNgtuII2aAkg7a7QS41jxPSZ17p4VdGnMHk3MQ==",
      "license": "MIT",
      "engines": {
        "node": ">=0.4.0"
      }
    },
    "node_modules/dunder-proto": {
      "version": "1.0.1",
      "resolved": "https://registry.npmmirror.com/dunder-proto/-/dunder-proto-1.0.1.tgz",
      "integrity": "sha512-KIN/nDJBQRcXw0MLVhZE9iQHmG68qAVIBg9CqmUYjmQIhgij9U5MFvrqkUL5FbtyyzZuOeOt0zdeRe4UY7ct+A==",
      "license": "MIT",
      "dependencies": {
        "call-bind-apply-helpers": "^1.0.1",
        "es-errors": "^1.3.0",
        "gopd": "^1.2.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/echarts": {
      "version": "6.1.0",
      "resolved": "https://registry.npmmirror.com/echarts/-/echarts-6.1.0.tgz",
      "integrity": "sha512-q0yaFPggC9FUdsWH4blavRWFmxdrIodbkoKNAjJudAI6CA9gNPxHtV2RcZNEepZVlk4yvBYkOkbk6HIVpIyHZA==",
      "license": "Apache-2.0",
      "dependencies": {
        "tslib": "2.3.0",
        "zrender": "6.1.0"
      }
    },
    "node_modules/element-plus": {
      "version": "2.13.5",
      "resolved": "https://registry.npmmirror.com/element-plus/-/element-plus-2.13.5.tgz",
      "integrity": "sha512-dmY24fhSREfZN/PuUt0YZigMso7wWzl+B5o+YKNN15kQIn/0hzamsPU+ebj9SES0IbUqsLX1wkrzYmzU8VrVOQ==",
      "license": "MIT",
      "dependencies": {
        "@ctrl/tinycolor": "^4.2.0",
        "@element-plus/icons-vue": "^2.3.2",
        "@floating-ui/dom": "^1.0.1",
        "@popperjs/core": "npm:@sxzz/popperjs-es@^2.11.7",
        "@types/lodash": "^4.17.20",
        "@types/lodash-es": "^4.17.12",
        "@vueuse/core": "12.0.0",
        "async-validator": "^4.2.5",
        "dayjs": "^1.11.19",
        "lodash": "^4.17.23",
        "lodash-es": "^4.17.23",
        "lodash-unified": "^1.0.3",
        "memoize-one": "^6.0.0",
        "normalize-wheel-es": "^1.2.0"
      },
      "peerDependencies": {
        "vue": "^3.3.0"
      }
    },
    "node_modules/entities": {
      "version": "7.0.1",
      "resolved": "https://registry.npmmirror.com/entities/-/entities-7.0.1.tgz",
      "integrity": "sha512-TWrgLOFUQTH994YUyl1yT4uyavY5nNB5muff+RtWaqNVCAK408b5ZnnbNAUEWLTCpum9w6arT70i1XdQ4UeOPA==",
      "license": "BSD-2-Clause",
      "engines": {
        "node": ">=0.12"
      },
      "funding": {
        "url": "https://github.com/fb55/entities?sponsor=1"
      }
    },
    "node_modules/es-define-property": {
      "version": "1.0.1",
      "resolved": "https://registry.npmmirror.com/es-define-property/-/es-define-property-1.0.1.tgz",
      "integrity": "sha512-e3nRfgfUZ4rNGL232gUgX06QNyyez04KdjFrF+LTRoOXmrOgFKDg4BCdsjW8EnT69eqdYGmRpJwiPVYNrCaW3g==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/es-errors": {
      "version": "1.3.0",
      "resolved": "https://registry.npmmirror.com/es-errors/-/es-errors-1.3.0.tgz",
      "integrity": "sha512-Zf5H2Kxt2xjTvbJvP2ZWLEICxA6j+hAmMzIlypy4xcBg1vKVnx89Wy0GbS+kf5cwCVFFzdCFh2XSCFNULS6csw==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/es-object-atoms": {
      "version": "1.1.1",
      "resolved": "https://registry.npmmirror.com/es-object-atoms/-/es-object-atoms-1.1.1.tgz",
      "integrity": "sha512-FGgH2h8zKNim9ljj7dankFPcICIK9Cp5bm+c2gQSYePhpaG5+esrLODihIorn+Pe6FGJzWhXQotPv73jTaldXA==",
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/es-set-tostringtag": {
      "version": "2.1.0",
      "resolved": "https://registry.npmmirror.com/es-set-tostringtag/-/es-set-tostringtag-2.1.0.tgz",
      "integrity": "sha512-j6vWzfrGVfyXxge+O0x5sh6cvxAog0a/4Rdd2K36zCMV5eJ+/+tOAngRO8cODMNWbVRdVlmGZQL2YS3yR8bIUA==",
      "license": "MIT",
      "dependencies": {
        "es-errors": "^1.3.0",
        "get-intrinsic": "^1.2.6",
        "has-tostringtag": "^1.0.2",
        "hasown": "^2.0.2"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/esbuild": {
      "version": "0.25.12",
      "resolved": "https://registry.npmmirror.com/esbuild/-/esbuild-0.25.12.tgz",
      "integrity": "sha512-bbPBYYrtZbkt6Os6FiTLCTFxvq4tt3JKall1vRwshA3fdVztsLAatFaZobhkBC8/BrPetoa0oksYoKXoG4ryJg==",
      "dev": true,
      "hasInstallScript": true,
      "license": "MIT",
      "bin": {
        "esbuild": "bin/esbuild"
      },
      "engines": {
        "node": ">=18"
      },
      "optionalDependencies": {
        "@esbuild/aix-ppc64": "0.25.12",
        "@esbuild/android-arm": "0.25.12",
        "@esbuild/android-arm64": "0.25.12",
        "@esbuild/android-x64": "0.25.12",
        "@esbuild/darwin-arm64": "0.25.12",
        "@esbuild/darwin-x64": "0.25.12",
        "@esbuild/freebsd-arm64": "0.25.12",
        "@esbuild/freebsd-x64": "0.25.12",
        "@esbuild/linux-arm": "0.25.12",
        "@esbuild/linux-arm64": "0.25.12",
        "@esbuild/linux-ia32": "0.25.12",
        "@esbuild/linux-loong64": "0.25.12",
        "@esbuild/linux-mips64el": "0.25.12",
        "@esbuild/linux-ppc64": "0.25.12",
        "@esbuild/linux-riscv64": "0.25.12",
        "@esbuild/linux-s390x": "0.25.12",
        "@esbuild/linux-x64": "0.25.12",
        "@esbuild/netbsd-arm64": "0.25.12",
        "@esbuild/netbsd-x64": "0.25.12",
        "@esbuild/openbsd-arm64": "0.25.12",
        "@esbuild/openbsd-x64": "0.25.12",
        "@esbuild/openharmony-arm64": "0.25.12",
        "@esbuild/sunos-x64": "0.25.12",
        "@esbuild/win32-arm64": "0.25.12",
        "@esbuild/win32-ia32": "0.25.12",
        "@esbuild/win32-x64": "0.25.12"
      }
    },
    "node_modules/estree-walker": {
      "version": "2.0.2",
      "resolved": "https://registry.npmmirror.com/estree-walker/-/estree-walker-2.0.2.tgz",
      "integrity": "sha512-Rfkk/Mp/DL7JVje3u18FxFujQlTNR2q6QfMSMB7AvCBx91NGj/ba3kCfza0f6dVDbw7YlRf/nDrn7pQrCCyQ/w==",
      "license": "MIT"
    },
    "node_modules/exsolve": {
      "version": "1.0.8",
      "resolved": "https://registry.npmmirror.com/exsolve/-/exsolve-1.0.8.tgz",
      "integrity": "sha512-LmDxfWXwcTArk8fUEnOfSZpHOJ6zOMUJKOtFLFqJLoKJetuQG874Uc7/Kki7zFLzYybmZhp1M7+98pfMqeX8yA==",
      "license": "MIT"
    },
    "node_modules/fdir": {
      "version": "6.5.0",
      "resolved": "https://registry.npmmirror.com/fdir/-/fdir-6.5.0.tgz",
      "integrity": "sha512-tIbYtZbucOs0BRGqPJkshJUYdL+SDH7dVM8gjy+ERp3WAUjLEFJE+02kanyHtwjWOnwrKYBiwAmM0p4kLJAnXg==",
      "license": "MIT",
      "engines": {
        "node": ">=12.0.0"
      },
      "peerDependencies": {
        "picomatch": "^3 || ^4"
      },
      "peerDependenciesMeta": {
        "picomatch": {
          "optional": true
        }
      }
    },
    "node_modules/follow-redirects": {
      "version": "1.15.11",
      "resolved": "https://registry.npmmirror.com/follow-redirects/-/follow-redirects-1.15.11.tgz",
      "integrity": "sha512-deG2P0JfjrTxl50XGCDyfI97ZGVCxIpfKYmfyrQ54n5FO/0gfIES8C/Psl6kWVDolizcaaxZJnTS0QSMxvnsBQ==",
      "funding": [
        {
          "type": "individual",
          "url": "https://github.com/sponsors/RubenVerborgh"
        }
      ],
      "license": "MIT",
      "engines": {
        "node": ">=4.0"
      },
      "peerDependenciesMeta": {
        "debug": {
          "optional": true
        }
      }
    },
    "node_modules/form-data": {
      "version": "4.0.5",
      "resolved": "https://registry.npmmirror.com/form-data/-/form-data-4.0.5.tgz",
      "integrity": "sha512-8RipRLol37bNs2bhoV67fiTEvdTrbMUYcFTiy3+wuuOnUog2QBHCZWXDRijWQfAkhBj2Uf5UnVaiWwA5vdd82w==",
      "license": "MIT",
      "dependencies": {
        "asynckit": "^0.4.0",
        "combined-stream": "^1.0.8",
        "es-set-tostringtag": "^2.1.0",
        "hasown": "^2.0.2",
        "mime-types": "^2.1.12"
      },
      "engines": {
        "node": ">= 6"
      }
    },
    "node_modules/fsevents": {
      "version": "2.3.3",
      "resolved": "https://registry.npmmirror.com/fsevents/-/fsevents-2.3.3.tgz",
      "integrity": "sha512-5xoDfX+fL7faATnagmWPpbFtwh/R77WmMMqqHGS65C3vvB0YHrgF+B1YmZ3441tMj5n63k0212XNoJwzlhffQw==",
      "dev": true,
      "hasInstallScript": true,
      "license": "MIT",
      "optional": true,
      "os": [
        "darwin"
      ],
      "engines": {
        "node": "^8.16.0 || ^10.6.0 || >=11.0.0"
      }
    },
    "node_modules/function-bind": {
      "version": "1.1.2",
      "resolved": "https://registry.npmmirror.com/function-bind/-/function-bind-1.1.2.tgz",
      "integrity": "sha512-7XHNxH7qX9xG5mIwxkhumTox/MIRNcOgDrxWsMt2pAr23WHp6MrRlN7FBSFpCpr+oVO0F744iUgR82nJMfG2SA==",
      "license": "MIT",
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/get-intrinsic": {
      "version": "1.3.0",
      "resolved": "https://registry.npmmirror.com/get-intrinsic/-/get-intrinsic-1.3.0.tgz",
      "integrity": "sha512-9fSjSaos/fRIVIp+xSJlE6lfwhES7LNtKaCBIamHsjr2na1BiABJPo0mOjjz8GJDURarmCPGqaiVg5mfjb98CQ==",
      "license": "MIT",
      "dependencies": {
        "call-bind-apply-helpers": "^1.0.2",
        "es-define-property": "^1.0.1",
        "es-errors": "^1.3.0",
        "es-object-atoms": "^1.1.1",
        "function-bind": "^1.1.2",
        "get-proto": "^1.0.1",
        "gopd": "^1.2.0",
        "has-symbols": "^1.1.0",
        "hasown": "^2.0.2",
        "math-intrinsics": "^1.1.0"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/get-proto": {
      "version": "1.0.1",
      "resolved": "https://registry.npmmirror.com/get-proto/-/get-proto-1.0.1.tgz",
      "integrity": "sha512-sTSfBjoXBp89JvIKIefqw7U2CCebsc74kiY6awiGogKtoSGbgjYE/G/+l9sF3MWFPNc9IcoOC4ODfKHfxFmp0g==",
      "license": "MIT",
      "dependencies": {
        "dunder-proto": "^1.0.1",
        "es-object-atoms": "^1.0.0"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/gopd": {
      "version": "1.2.0",
      "resolved": "https://registry.npmmirror.com/gopd/-/gopd-1.2.0.tgz",
      "integrity": "sha512-ZUKRh6/kUFoAiTAtTYPZJ3hw9wNxx+BIBOijnlG9PnrJsCcSjs1wyyD6vJpaYtgnzDrKYRSqf3OO6Rfa93xsRg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/has-symbols": {
      "version": "1.1.0",
      "resolved": "https://registry.npmmirror.com/has-symbols/-/has-symbols-1.1.0.tgz",
      "integrity": "sha512-1cDNdwJ2Jaohmb3sg4OmKaMBwuC48sYni5HUw2DvsC8LjGTLK9h+eb1X6RyuOHe4hT0ULCW68iomhjUoKUqlPQ==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/has-tostringtag": {
      "version": "1.0.2",
      "resolved": "https://registry.npmmirror.com/has-tostringtag/-/has-tostringtag-1.0.2.tgz",
      "integrity": "sha512-NqADB8VjPFLM2V0VvHUewwwsw0ZWBaIdgo+ieHtK3hasLz4qeCRjYcqfB6AQrBggRKppKF8L52/VqdVsO47Dlw==",
      "license": "MIT",
      "dependencies": {
        "has-symbols": "^1.0.3"
      },
      "engines": {
        "node": ">= 0.4"
      },
      "funding": {
        "url": "https://github.com/sponsors/ljharb"
      }
    },
    "node_modules/hasown": {
      "version": "2.0.2",
      "resolved": "https://registry.npmmirror.com/hasown/-/hasown-2.0.2.tgz",
      "integrity": "sha512-0hJU9SCPvmMzIBdZFqNPXWa6dqh7WdH0cII9y+CyS8rG3nL48Bclra9HmKhVVUHyPWNH5Y7xDwAB7bfgSjkUMQ==",
      "license": "MIT",
      "dependencies": {
        "function-bind": "^1.1.2"
      },
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/hookable": {
      "version": "5.5.3",
      "resolved": "https://registry.npmmirror.com/hookable/-/hookable-5.5.3.tgz",
      "integrity": "sha512-Yc+BQe8SvoXH1643Qez1zqLRmbA5rCL+sSmk6TVos0LWVfNIB7PGncdlId77WzLGSIB5KaWgTaNTs2lNVEI6VQ==",
      "license": "MIT"
    },
    "node_modules/is-what": {
      "version": "5.5.0",
      "resolved": "https://registry.npmmirror.com/is-what/-/is-what-5.5.0.tgz",
      "integrity": "sha512-oG7cgbmg5kLYae2N5IVd3jm2s+vldjxJzK1pcu9LfpGuQ93MQSzo0okvRna+7y5ifrD+20FE8FvjusyGaz14fw==",
      "license": "MIT",
      "engines": {
        "node": ">=18"
      },
      "funding": {
        "url": "https://github.com/sponsors/mesqueeb"
      }
    },
    "node_modules/jsesc": {
      "version": "3.1.0",
      "resolved": "https://registry.npmmirror.com/jsesc/-/jsesc-3.1.0.tgz",
      "integrity": "sha512-/sM3dO2FOzXjKQhJuo0Q173wf2KOo8t4I8vHy6lF9poUp7bKT0/NHE8fPX23PwfhnykfqnC2xRxOnVw5XuGIaA==",
      "license": "MIT",
      "bin": {
        "jsesc": "bin/jsesc"
      },
      "engines": {
        "node": ">=6"
      }
    },
    "node_modules/json5": {
      "version": "2.2.3",
      "resolved": "https://registry.npmmirror.com/json5/-/json5-2.2.3.tgz",
      "integrity": "sha512-XmOWe7eyHYH14cLdVPoyg+GOH3rYX++KpzrylJwSW98t3Nk+U8XOl8FWKOgwtzdb8lXGf6zYwDUzeHMWfxasyg==",
      "license": "MIT",
      "bin": {
        "json5": "lib/cli.js"
      },
      "engines": {
        "node": ">=6"
      }
    },
    "node_modules/local-pkg": {
      "version": "1.1.2",
      "resolved": "https://registry.npmmirror.com/local-pkg/-/local-pkg-1.1.2.tgz",
      "integrity": "sha512-arhlxbFRmoQHl33a0Zkle/YWlmNwoyt6QNZEIJcqNbdrsix5Lvc4HyyI3EnwxTYlZYc32EbYrQ8SzEZ7dqgg9A==",
      "license": "MIT",
      "dependencies": {
        "mlly": "^1.7.4",
        "pkg-types": "^2.3.0",
        "quansync": "^0.2.11"
      },
      "engines": {
        "node": ">=14"
      },
      "funding": {
        "url": "https://github.com/sponsors/antfu"
      }
    },
    "node_modules/lodash": {
      "version": "4.17.23",
      "resolved": "https://registry.npmmirror.com/lodash/-/lodash-4.17.23.tgz",
      "integrity": "sha512-LgVTMpQtIopCi79SJeDiP0TfWi5CNEc/L/aRdTh3yIvmZXTnheWpKjSZhnvMl8iXbC1tFg9gdHHDMLoV7CnG+w==",
      "license": "MIT"
    },
    "node_modules/lodash-es": {
      "version": "4.17.23",
      "resolved": "https://registry.npmmirror.com/lodash-es/-/lodash-es-4.17.23.tgz",
      "integrity": "sha512-kVI48u3PZr38HdYz98UmfPnXl2DXrpdctLrFLCd3kOx1xUkOmpFPx7gCWWM5MPkL/fD8zb+Ph0QzjGFs4+hHWg==",
      "license": "MIT"
    },
    "node_modules/lodash-unified": {
      "version": "1.0.3",
      "resolved": "https://registry.npmmirror.com/lodash-unified/-/lodash-unified-1.0.3.tgz",
      "integrity": "sha512-WK9qSozxXOD7ZJQlpSqOT+om2ZfcT4yO+03FuzAHD0wF6S0l0090LRPDx3vhTTLZ8cFKpBn+IOcVXK6qOcIlfQ==",
      "license": "MIT",
      "peerDependencies": {
        "@types/lodash-es": "*",
        "lodash": "*",
        "lodash-es": "*"
      }
    },
    "node_modules/magic-string": {
      "version": "0.30.21",
      "resolved": "https://registry.npmmirror.com/magic-string/-/magic-string-0.30.21.tgz",
      "integrity": "sha512-vd2F4YUyEXKGcLHoq+TEyCjxueSeHnFxyyjNp80yg0XV4vUhnDer/lvvlqM/arB5bXQN5K2/3oinyCRyx8T2CQ==",
      "license": "MIT",
      "dependencies": {
        "@jridgewell/sourcemap-codec": "^1.5.5"
      }
    },
    "node_modules/magic-string-ast": {
      "version": "1.0.3",
      "resolved": "https://registry.npmmirror.com/magic-string-ast/-/magic-string-ast-1.0.3.tgz",
      "integrity": "sha512-CvkkH1i81zl7mmb94DsRiFeG9V2fR2JeuK8yDgS8oiZSFa++wWLEgZ5ufEOyLHbvSbD1gTRKv9NdX69Rnvr9JA==",
      "license": "MIT",
      "dependencies": {
        "magic-string": "^0.30.19"
      },
      "engines": {
        "node": ">=20.19.0"
      },
      "funding": {
        "url": "https://github.com/sponsors/sxzz"
      }
    },
    "node_modules/math-intrinsics": {
      "version": "1.1.0",
      "resolved": "https://registry.npmmirror.com/math-intrinsics/-/math-intrinsics-1.1.0.tgz",
      "integrity": "sha512-/IXtbwEk5HTPyEwyKX6hGkYXxM9nbj64B+ilVJnC/R6B0pH5G4V3b0pVbL7DBj4tkhBAppbQUlf6F6Xl9LHu1g==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.4"
      }
    },
    "node_modules/memoize-one": {
      "version": "6.0.0",
      "resolved": "https://registry.npmmirror.com/memoize-one/-/memoize-one-6.0.0.tgz",
      "integrity": "sha512-rkpe71W0N0c0Xz6QD0eJETuWAJGnJ9afsl1srmwPrI+yBCkge5EycXXbYRyvL29zZVUWQCY7InPRCv3GDXuZNw==",
      "license": "MIT"
    },
    "node_modules/mime-db": {
      "version": "1.52.0",
      "resolved": "https://registry.npmmirror.com/mime-db/-/mime-db-1.52.0.tgz",
      "integrity": "sha512-sPU4uV7dYlvtWJxwwxHD0PuihVNiE7TyAbQ5SWxDCB9mUYvOgroQOwYQQOKPJ8CIbE+1ETVlOoK1UC2nU3gYvg==",
      "license": "MIT",
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/mime-types": {
      "version": "2.1.35",
      "resolved": "https://registry.npmmirror.com/mime-types/-/mime-types-2.1.35.tgz",
      "integrity": "sha512-ZDY+bPm5zTTF+YpCrAU9nK0UgICYPT0QtT1NZWFv4s++TNkcgVaT0g6+4R2uI4MjQjzysHB1zxuWL50hzaeXiw==",
      "license": "MIT",
      "dependencies": {
        "mime-db": "1.52.0"
      },
      "engines": {
        "node": ">= 0.6"
      }
    },
    "node_modules/mitt": {
      "version": "3.0.1",
      "resolved": "https://registry.npmmirror.com/mitt/-/mitt-3.0.1.tgz",
      "integrity": "sha512-vKivATfr97l2/QBCYAkXYDbrIWPM2IIKEl7YPhjCvKlG3kE2gm+uBo6nEXK3M5/Ffh/FLpKExzOQ3JJoJGFKBw==",
      "license": "MIT"
    },
    "node_modules/mlly": {
      "version": "1.8.1",
      "resolved": "https://registry.npmmirror.com/mlly/-/mlly-1.8.1.tgz",
      "integrity": "sha512-SnL6sNutTwRWWR/vcmCYHSADjiEesp5TGQQ0pXyLhW5IoeibRlF/CbSLailbB3CNqJUk9cVJ9dUDnbD7GrcHBQ==",
      "license": "MIT",
      "dependencies": {
        "acorn": "^8.16.0",
        "pathe": "^2.0.3",
        "pkg-types": "^1.3.1",
        "ufo": "^1.6.3"
      }
    },
    "node_modules/mlly/node_modules/confbox": {
      "version": "0.1.8",
      "resolved": "https://registry.npmmirror.com/confbox/-/confbox-0.1.8.tgz",
      "integrity": "sha512-RMtmw0iFkeR4YV+fUOSucriAQNb9g8zFR52MWCtl+cCZOFRNL6zeB395vPzFhEjjn4fMxXudmELnl/KF/WrK6w==",
      "license": "MIT"
    },
    "node_modules/mlly/node_modules/pkg-types": {
      "version": "1.3.1",
      "resolved": "https://registry.npmmirror.com/pkg-types/-/pkg-types-1.3.1.tgz",
      "integrity": "sha512-/Jm5M4RvtBFVkKWRu2BLUTNP8/M2a+UwuAX+ae4770q1qVGtfjG+WTCupoZixokjmHiry8uI+dlY8KXYV5HVVQ==",
      "license": "MIT",
      "dependencies": {
        "confbox": "^0.1.8",
        "mlly": "^1.7.4",
        "pathe": "^2.0.1"
      }
    },
    "node_modules/muggle-string": {
      "version": "0.4.1",
      "resolved": "https://registry.npmmirror.com/muggle-string/-/muggle-string-0.4.1.tgz",
      "integrity": "sha512-VNTrAak/KhO2i8dqqnqnAHOa3cYBwXEZe9h+D5h/1ZqFSTEFHdM65lR7RoIqq3tBBYavsOXV84NoHXZ0AkPyqQ==",
      "license": "MIT"
    },
    "node_modules/nanoid": {
      "version": "3.3.11",
      "resolved": "https://registry.npmmirror.com/nanoid/-/nanoid-3.3.11.tgz",
      "integrity": "sha512-N8SpfPUnUp1bK+PMYW8qSWdl9U+wwNWI4QKxOYDy9JAro3WMX7p2OeVRF9v+347pnakNevPmiHhNmZ2HbFA76w==",
      "funding": [
        {
          "type": "github",
          "url": "https://github.com/sponsors/ai"
        }
      ],
      "license": "MIT",
      "bin": {
        "nanoid": "bin/nanoid.cjs"
      },
      "engines": {
        "node": "^10 || ^12 || ^13.7 || ^14 || >=15.0.1"
      }
    },
    "node_modules/normalize-wheel-es": {
      "version": "1.2.0",
      "resolved": "https://registry.npmmirror.com/normalize-wheel-es/-/normalize-wheel-es-1.2.0.tgz",
      "integrity": "sha512-Wj7+EJQ8mSuXr2iWfnujrimU35R2W4FAErEyTmJoJ7ucwTn2hOUSsRehMb5RSYkxXGTM7Y9QpvPmp++w5ftoJw==",
      "license": "BSD-3-Clause"
    },
    "node_modules/pathe": {
      "version": "2.0.3",
      "resolved": "https://registry.npmmirror.com/pathe/-/pathe-2.0.3.tgz",
      "integrity": "sha512-WUjGcAqP1gQacoQe+OBJsFA7Ld4DyXuUIjZ5cc75cLHvJ7dtNsTugphxIADwspS+AraAUePCKrSVtPLFj/F88w==",
      "license": "MIT"
    },
    "node_modules/perfect-debounce": {
      "version": "1.0.0",
      "resolved": "https://registry.npmmirror.com/perfect-debounce/-/perfect-debounce-1.0.0.tgz",
      "integrity": "sha512-xCy9V055GLEqoFaHoC1SoLIaLmWctgCUaBaWxDZ7/Zx4CTyX7cJQLJOok/orfjZAh9kEYpjJa4d0KcJmCbctZA==",
      "license": "MIT"
    },
    "node_modules/picocolors": {
      "version": "1.1.1",
      "resolved": "https://registry.npmmirror.com/picocolors/-/picocolors-1.1.1.tgz",
      "integrity": "sha512-xceH2snhtb5M9liqDsmEw56le376mTZkEX/jEb/RxNFyegNul7eNslCXP9FDj/Lcu0X8KEyMceP2ntpaHrDEVA==",
      "license": "ISC"
    },
    "node_modules/picomatch": {
      "version": "4.0.3",
      "resolved": "https://registry.npmmirror.com/picomatch/-/picomatch-4.0.3.tgz",
      "integrity": "sha512-5gTmgEY/sqK6gFXLIsQNH19lWb4ebPDLA4SdLP7dsWkIXHWlG66oPuVvXSGFPppYZz8ZDZq0dYYrbHfBCVUb1Q==",
      "license": "MIT",
      "engines": {
        "node": ">=12"
      },
      "funding": {
        "url": "https://github.com/sponsors/jonschlinkert"
      }
    },
    "node_modules/pinia": {
      "version": "3.0.4",
      "resolved": "https://registry.npmmirror.com/pinia/-/pinia-3.0.4.tgz",
      "integrity": "sha512-l7pqLUFTI/+ESXn6k3nu30ZIzW5E2WZF/LaHJEpoq6ElcLD+wduZoB2kBN19du6K/4FDpPMazY2wJr+IndBtQw==",
      "license": "MIT",
      "dependencies": {
        "@vue/devtools-api": "^7.7.7"
      },
      "funding": {
        "url": "https://github.com/sponsors/posva"
      },
      "peerDependencies": {
        "typescript": ">=4.5.0",
        "vue": "^3.5.11"
      },
      "peerDependenciesMeta": {
        "typescript": {
          "optional": true
        }
      }
    },
    "node_modules/pkg-types": {
      "version": "2.3.0",
      "resolved": "https://registry.npmmirror.com/pkg-types/-/pkg-types-2.3.0.tgz",
      "integrity": "sha512-SIqCzDRg0s9npO5XQ3tNZioRY1uK06lA41ynBC1YmFTmnY6FjUjVt6s4LoADmwoig1qqD0oK8h1p/8mlMx8Oig==",
      "license": "MIT",
      "dependencies": {
        "confbox": "^0.2.2",
        "exsolve": "^1.0.7",
        "pathe": "^2.0.3"
      }
    },
    "node_modules/postcss": {
      "version": "8.5.8",
      "resolved": "https://registry.npmmirror.com/postcss/-/postcss-8.5.8.tgz",
      "integrity": "sha512-OW/rX8O/jXnm82Ey1k44pObPtdblfiuWnrd8X7GJ7emImCOstunGbXUpp7HdBrFQX6rJzn3sPT397Wp5aCwCHg==",
      "funding": [
        {
          "type": "opencollective",
          "url": "https://opencollective.com/postcss/"
        },
        {
          "type": "tidelift",
          "url": "https://tidelift.com/funding/github/npm/postcss"
        },
        {
          "type": "github",
          "url": "https://github.com/sponsors/ai"
        }
      ],
      "license": "MIT",
      "dependencies": {
        "nanoid": "^3.3.11",
        "picocolors": "^1.1.1",
        "source-map-js": "^1.2.1"
      },
      "engines": {
        "node": "^10 || ^12 || >=14"
      }
    },
    "node_modules/proxy-from-env": {
      "version": "1.1.0",
      "resolved": "https://registry.npmmirror.com/proxy-from-env/-/proxy-from-env-1.1.0.tgz",
      "integrity": "sha512-D+zkORCbA9f1tdWRK0RaCR3GPv50cMxcrz4X8k5LTSUD1Dkw47mKJEZQNunItRTkWwgtaUSo1RVFRIG9ZXiFYg==",
      "license": "MIT"
    },
    "node_modules/quansync": {
      "version": "0.2.11",
      "resolved": "https://registry.npmmirror.com/quansync/-/quansync-0.2.11.tgz",
      "integrity": "sha512-AifT7QEbW9Nri4tAwR5M/uzpBuqfZf+zwaEM/QkzEjj7NBuFD2rBuy0K3dE+8wltbezDV7JMA0WfnCPYRSYbXA==",
      "funding": [
        {
          "type": "individual",
          "url": "https://github.com/sponsors/antfu"
        },
        {
          "type": "individual",
          "url": "https://github.com/sponsors/sxzz"
        }
      ],
      "license": "MIT"
    },
    "node_modules/readdirp": {
      "version": "5.0.0",
      "resolved": "https://registry.npmmirror.com/readdirp/-/readdirp-5.0.0.tgz",
      "integrity": "sha512-9u/XQ1pvrQtYyMpZe7DXKv2p5CNvyVwzUB6uhLAnQwHMSgKMBR62lc7AHljaeteeHXn11XTAaLLUVZYVZyuRBQ==",
      "license": "MIT",
      "engines": {
        "node": ">= 20.19.0"
      },
      "funding": {
        "type": "individual",
        "url": "https://paulmillr.com/funding/"
      }
    },
    "node_modules/rfdc": {
      "version": "1.4.1",
      "resolved": "https://registry.npmmirror.com/rfdc/-/rfdc-1.4.1.tgz",
      "integrity": "sha512-q1b3N5QkRUWUl7iyylaaj3kOpIT0N2i9MqIEQXP73GVsN9cw3fdx8X63cEmWhJGi2PPCF23Ijp7ktmd39rawIA==",
      "license": "MIT"
    },
    "node_modules/rollup": {
      "version": "4.59.0",
      "resolved": "https://registry.npmmirror.com/rollup/-/rollup-4.59.0.tgz",
      "integrity": "sha512-2oMpl67a3zCH9H79LeMcbDhXW/UmWG/y2zuqnF2jQq5uq9TbM9TVyXvA4+t+ne2IIkBdrLpAaRQAvo7YI/Yyeg==",
      "dev": true,
      "license": "MIT",
      "dependencies": {
        "@types/estree": "1.0.8"
      },
      "bin": {
        "rollup": "dist/bin/ro

[file truncated because it is too long]

````

---

## Frontend/package.json

````text
{
  "name": "smartlab-front",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "@vue-flow/background": "^1.3.2",
    "@vue-flow/controls": "^1.1.3",
    "@vue-flow/core": "^1.48.2",
    "axios": "^1.13.6",
    "echarts": "^6.1.0",
    "element-plus": "^2.13.5",
    "pinia": "^3.0.4",
    "vue": "^3.5.13",
    "vue-router": "^5.0.3"
  },
  "devDependencies": {
    "@vitejs/plugin-vue": "^5.2.1",
    "vite": "^6.0.5"
  }
}

````

---

## Frontend/src/App.vue

````text
<template>
  <RouterView />
</template>

<style>
/* 你的全局样式 */
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html,
body,
#app {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}
</style>
````

---

## Frontend/src/components/device/DeviceCategorySidebar.vue

````text
<template>
    <div class="sidebar-container">
        <div class="sidebar-header">
            <h3 class="title">设备分类</h3>
        </div>

        <div class="tree-wrapper">
            <el-tree :data="deviceCategories" :props="defaultProps" node-key="deviceClassId"
                :current-node-key="selectedCategory?.deviceClassId" highlight-current default-expand-all
                :expand-on-click-node="false" @node-click="handleNodeClick" class="category-tree">
                <template #default="{ node, data }">
                    <div class="custom-tree-node">
                        <el-icon class="node-icon">
                            <Folder v-if="data.children && data.children.length > 0" />
                            <Document v-else />
                        </el-icon>
                        <span class="node-label">{{ node.label }}</span>
                    </div>
                </template>
            </el-tree>

            <el-empty v-if="!deviceCategories || deviceCategories.length === 0" description="暂无设备分类" :image-size="60" />
        </div>
    </div>
</template>

<script setup>
import { Folder, Document } from '@element-plus/icons-vue';

defineProps({
    deviceCategories: {
        type: Array,
        required: true,
        default: () => []
    },
    selectedCategory: {
        type: Object,
        default: null
    }
});

const emit = defineEmits(['select-category', 'add-category']);

// el-tree 的配置项，告诉它读取哪个字段作为显示文本和子节点
const defaultProps = {
    children: 'children',
    label: 'deviceClassName'
};

// 点击树节点时，触发父组件的方法
const handleNodeClick = (data) => {
    emit('select-category', data);
};
</script>

<style scoped>
.sidebar-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    background-color: #ffffff;
}

.sidebar-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 20px;
    border-bottom: 1px solid #f0f2f5;
}

.sidebar-header .title {
    margin: 0;
    font-size: 16px;
    font-weight: 600;
    color: #2c3e50;
}

.tree-wrapper {
    flex: 1;
    overflow-y: auto;
    padding: 15px 10px;
}

/* 美化 Element Plus 的 Tree 组件 */
.category-tree {
    background: transparent;
}

:deep(.el-tree-node__content) {
    height: 38px;
    border-radius: 6px;
    margin-bottom: 2px;
    transition: background-color 0.2s;
}

:deep(.el-tree-node.is-current > .el-tree-node__content) {
    background-color: #ecf5ff !important;
    color: #409EFF;
    font-weight: bold;
}

.custom-tree-node {
    display: flex;
    align-items: center;
    font-size: 14px;
    width: 100%;
}

.node-icon {
    margin-right: 8px;
    font-size: 16px;
    color: #909399;
}

:deep(.is-current) .node-icon {
    color: #409EFF;
}

.node-label {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

/* 侧边栏滚动条美化 */
.tree-wrapper::-webkit-scrollbar {
    width: 6px;
}

.tree-wrapper::-webkit-scrollbar-thumb {
    background-color: #dcdfe6;
    border-radius: 3px;
}

.tree-wrapper::-webkit-scrollbar-thumb:hover {
    background-color: #c0c4cc;
}
</style>

````

---

## Frontend/src/components/layout/AppTopNav.vue

````text
<template>
  <header class="top-navbar">
    <div class="navbar-brand">
      <div class="brand-icon">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
          <path d="M9 3H5a2 2 0 00-2 2v4m6-6h10a2 2 0 012 2v4M9 3v18m0 0h10a2 2 0 002-2V9M9 21H5a2 2 0 01-2-2V9m0 0h18"
            stroke="#60a5fa" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </div>
    </div>

    <nav class="navbar-menu-wrap">
      <el-menu
        mode="horizontal"
        router
        :default-active="activePath"
        class="navbar-menu"
        :ellipsis="false"
        background-color="transparent"
        active-text-color="#ffffff"
        text-color="rgba(255,255,255,0.72)"
      >
        <template v-for="item in authStore.menus" :key="item.name">
          <el-menu-item v-if="!item.children || item.children.length === 0" :index="item.path" class="nav-item">
            <el-icon class="nav-icon"><component :is="getMenuIcon(item.name)" /></el-icon>
            <span>{{ item.name }}</span>
          </el-menu-item>

          <el-sub-menu v-else :index="item.name" class="nav-item">
            <template #title>
              <el-icon class="nav-icon"><component :is="getMenuIcon(item.name)" /></el-icon>
              <span>{{ item.name }}</span>
            </template>
            <el-menu-item v-for="sub in item.children" :key="sub.name" :index="sub.path">
              <el-icon><component :is="getMenuIcon(sub.name)" /></el-icon>
              <span>{{ sub.name }}</span>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </nav>

    <div class="navbar-right">
      <div class="info-chip">
        <el-icon><OfficeBuilding /></el-icon>
        <span>{{ authStore.lab || 'SmartLab' }}</span>
      </div>
      <div class="info-chip clock-chip">
        <el-icon><Clock /></el-icon>
        <span class="mono-time">{{ currentTime }}</span>
      </div>
      <div class="nav-divider"></div>
      <el-dropdown trigger="click" @command="handleDropdown">
        <div class="user-trigger">
          <el-avatar :size="28" class="user-avatar">{{ authStore.username ? authStore.username[0].toUpperCase() : 'U' }}</el-avatar>
          <div class="user-info">
            <span class="user-name">{{ authStore.username }}</span>
            <span class="user-role">{{ authStore.userBasicInfo }}</span>
          </div>
          <el-icon class="chevron-icon"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </header>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'
import {
  ArrowDown,
  Clock,
  Connection,
  DataAnalysis,
  EditPen,
  HomeFilled,
  List,
  Monitor,
  OfficeBuilding,
  Setting,
  Share,
  SwitchButton,
  Tools,
  User,
  VideoPlay
} from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'

const authStore = useAuthStore()
const route = useRoute()
const router = useRouter()
const activePath = computed(() => route.path)
const currentTime = ref('')
let timerId = null

const updateTime = () => {
  currentTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
}

onMounted(() => {
  updateTime()
  timerId = setInterval(updateTime, 1000)
})

onUnmounted(() => {
  if (timerId) clearInterval(timerId)
})

const getMenuIcon = (name) => {
  const map = {
    首页: HomeFilled,
    设备中心: Monitor,
    设备模型管理: Tools,
    设备实例管理: Connection,
    设备执行代理: Connection,
    资源结构管理: Share,
    数据中心: DataAnalysis,
    任务中心: List,
    任务列表: List,
    任务监控: VideoPlay,
    流程设计: EditPen,
    流程设计器: EditPen,
    约束管理: Setting,
    用户管理: User
  }
  return map[name] || List
}

const handleDropdown = (command) => {
  if (command !== 'logout') return
  ElMessageBox.confirm('确认安全退出 SmartLab 系统吗?', '退出确认', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    authStore.clearAuth()
    router.push('/login')
  }).catch(() => {})
}
</script>

<style scoped>
.top-navbar {
  display: flex;
  align-items: center;
  height: 52px;
  min-height: 52px;
  background: #1a2d4e;
  border-bottom: 1px solid #142440;
  padding: 0 16px;
  z-index: 1000;
  box-shadow: 0 2px 8px rgba(0,0,0,0.22);
  flex-shrink: 0;
}

.navbar-brand { display: flex; align-items: center; flex-shrink: 0; margin-right: 24px; user-select: none; }
.brand-icon { display: flex; align-items: center; justify-content: center; width: 30px; height: 30px; background: rgba(96,165,250,0.12); border: 1px solid rgba(96,165,250,0.25); border-radius: 7px; }
.navbar-menu-wrap { flex: 1; min-width: 0; display: flex; align-items: center; height: 52px; overflow: hidden; }
.navbar-menu { background: transparent !important; border: none !important; height: 52px !important; display: flex; align-items: stretch; }

:deep(.el-menu--horizontal > .el-menu-item),
:deep(.el-menu--horizontal > .el-sub-menu .el-sub-menu__title) {
  height: 52px !important;
  line-height: 52px !important;
  border-bottom: 3px solid transparent !important;
  font-size: 0.855rem !important;
  font-weight: 500 !important;
  padding: 0 14px !important;
  color: rgba(255,255,255,0.72) !important;
  transition: color 0.15s, background 0.15s, border-color 0.15s !important;
}

:deep(.el-menu--horizontal > .el-menu-item:hover),
:deep(.el-menu--horizontal > .el-sub-menu:hover .el-sub-menu__title) { color: #ffffff !important; background: rgba(255,255,255,0.07) !important; }
:deep(.el-menu--horizontal > .el-menu-item.is-active) { color: #ffffff !important; border-bottom-color: #60a5fa !important; background: rgba(255,255,255,0.09) !important; }
:deep(.el-menu--horizontal > .el-sub-menu.is-active .el-sub-menu__title) { color: #ffffff !important; border-bottom-color: #60a5fa !important; }
:deep(.el-menu--popup) { background: #ffffff !important; border: 1px solid #d9dde6 !important; border-radius: 8px !important; box-shadow: 0 8px 24px rgba(0,0,0,0.12) !important; padding: 4px 0 !important; min-width: 150px !important; }
:deep(.el-menu--popup .el-menu-item) { color: #374151 !important; font-size: 0.875rem !important; height: 40px !important; line-height: 40px !important; padding: 0 18px !important; border-bottom: none !important; }
:deep(.el-menu--popup .el-menu-item:hover),
:deep(.el-menu--popup .el-menu-item.is-active) { background: #eff6ff !important; color: #1a6bbf !important; }

.nav-icon { font-size: 14px !important; margin-right: 4px !important; }
.navbar-right { display: flex; align-items: center; gap: 6px; flex-shrink: 0; margin-left: 16px; }
.info-chip { display: flex; align-items: center; gap: 5px; color: rgba(255,255,255,0.60); font-size: 0.775rem; padding: 3px 9px; background: rgba(255,255,255,0.05); border: 1px solid rgba(255,255,255,0.09); border-radius: 5px; white-space: nowrap; }
.info-chip .el-icon { font-size: 12px; }
.clock-chip { min-width: 92px; justify-content: center; }
.mono-time { font-family: 'JetBrains Mono', monospace; font-size: 0.78rem; letter-spacing: 0.04em; }
.nav-divider { width: 1px; height: 24px; background: rgba(255,255,255,0.14); margin: 0 6px; }
.user-trigger { display: flex; align-items: center; gap: 7px; padding: 4px 8px; border-radius: 6px; cursor: pointer; transition: background 0.15s; border: 1px solid rgba(255,255,255,0.09); background: rgba(255,255,255,0.04); }
.user-trigger:hover { background: rgba(255,255,255,0.10); border-color: rgba(255,255,255,0.16); }
.user-avatar { background: #1a6bbf !important; color: #fff !important; font-weight: 700 !important; font-size: 0.8rem !important; flex-shrink: 0; }
.user-info { display: flex; flex-direction: column; line-height: 1.2; }
.user-name { font-size: 0.80rem; font-weight: 600; color: #ffffff; white-space: nowrap; }
.user-role { font-size: 0.68rem; color: rgba(255,255,255,0.48); white-space: nowrap; }
.chevron-icon { color: rgba(255,255,255,0.40); font-size: 11px; }
</style>

````

---

## Frontend/src/components/layouts/MainLayout.vue

````text
<template>
  <div class="layout-container">
    <AppTopNav />
    <main class="content-area">
      <router-view v-slot="{ Component }">
        <transition name="fade-slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup>
import AppTopNav from '../layout/AppTopNav.vue'
</script>

<style scoped>
.layout-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  width: 100vw;
  background-color: var(--bg-primary);
  overflow: hidden;
}

.content-area {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary);
}

.fade-slide-enter-active,
.fade-slide-leave-active { transition: all 0.20s cubic-bezier(0.4, 0, 0.2, 1); }
.fade-slide-enter-from { opacity: 0; transform: translateY(6px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(-6px); }
</style>

````

---

## Frontend/src/main.js

````text
// 从Vue核心库中导入创建应用实例的方法
import { createApp } from 'vue'
// 从Pinia状态管理库中导入创建Pinia实例的方法
import { createPinia } from 'pinia'
// 导入全局样式（让整个应用都能用上这些样式）
import './style.css'
// 导入根组件App（整个应用的“总容器”）
import App from './App.vue'
// 导入路由模块（实现多页面会用到）
import router from './router';
// 导入Element Plus UI库和它的样式
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// // 导入Element Plus的图标组件（如果需要使用图标的话）
// import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 创建Pinia实例 → 让Vue应用使用Pinia状态管理库
const pinia = createPinia()

// 创建Vue应用实例 → 启用路由 → 挂载到#app上
const app = createApp(App);
// 注意：必须先挂载 pinia，再挂载 router，因为 router 的 beforeEach 中用到了 authStore
app.use(pinia);// 让应用拥有Pinia状态管理库的功能
app.use(router);// 让应用拥有路由能力
app.use(ElementPlus);// 让应用拥有Element Plus UI组件库的功能


// // 注册Element Plus图标组件（如果需要使用图标的话）
// for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
//   app.component(key, component)
// }

// 配置 Axios 全局拦截器以自动附加 JWT Token 并处理 401 认证失效
import axios from 'axios'
import { useAuthStore } from './stores/authStore'

axios.interceptors.request.use(config => {
  try {
    const authStore = useAuthStore()
    if (authStore.token) {
      config.headers['Authorization'] = 'Bearer ' + authStore.token
    }
  } catch (e) {
    // 忽略未完全初始化时的 Store 读取错误
  }
  return config
}, error => {
  return Promise.reject(error)
})

axios.interceptors.response.use(response => {
  return response
}, error => {
  if (error.response && error.response.status === 401) {
    try {
      const authStore = useAuthStore()
      authStore.clearAuth()
      router.push('/login')
    } catch (e) {
      localStorage.removeItem('smartlab_auth')
      window.location.hash = '#/login'
    }
  }
  return Promise.reject(error)
})

app.mount('#app'); // 把App组件渲染到index.html的#app元素里



````

---

## Frontend/src/router/index.js

````text
import { createRouter, createWebHashHistory } from 'vue-router'
import MainLayout from '../components/layouts/MainLayout.vue'
import { useAuthStore } from '../stores/authStore'

const routes = [
  { path: '/login', name: 'Login', component: () => import('../views/auth/Login.vue'), meta: { requiresAuth: false } },
  { path: '/register', name: 'Register', component: () => import('../views/auth/Register.vue'), meta: { requiresAuth: false } },
  {
    path: '/',
    name: 'MainLayout',
    component: MainLayout,
    redirect: '/home',
    meta: { requiresAuth: true },
    children: [
      { path: 'home', name: 'Home', component: () => import('../views/dashboard/Dashboard.vue'), meta: { title: '首页' } },
      { path: 'device-model-management', name: 'DeviceModelManagement', component: () => import('../views/device/DeviceModelManagement.vue'), meta: { title: '设备模型管理' } },
      { path: 'device-instance-management', name: 'DeviceInstanceManagement', component: () => import('../views/device/DeviceInstanceManagement.vue'), meta: { title: '设备实例管理' } },
      { path: 'adapter-management', name: 'AdapterManagement', component: () => import('../views/device/AdapterManagement.vue'), meta: { title: '设备执行代理' } },
      { path: 'data-management', name: 'DataManagement', component: () => import('../views/data/DataManagement.vue'), meta: { title: '数据中心' } },
      { path: 'task-management', name: 'TaskManagement', component: () => import('../views/task/TaskList.vue'), meta: { title: '任务列表' } },
      { path: 'task-designer', name: 'TaskDesigner', component: () => import('../views/task/WorkflowDesigner.vue'), meta: { title: '流程设计' } },
      { path: 'constraint-management', name: 'ConstraintManagement', component: () => import('../views/security/SecurityCenter.vue'), meta: { title: '约束管理' } },
      { path: 'user-management', name: 'UserManagement', component: () => import('../views/admin/UserManagement.vue'), meta: { title: '用户管理' } }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'NotFound', redirect: '/home' }
]

const router = createRouter({ history: createWebHashHistory(), routes })

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)

  if (requiresAuth && !authStore.isAuthenticated) return '/login'
  if ((to.path === '/login' || to.path === '/register') && authStore.isAuthenticated) return authStore.firstVisiblePath()

  if (requiresAuth && authStore.isAuthenticated) {
    if (!authStore.menuLoaded) await authStore.refreshProfile()
    if (!authStore.isAuthenticated) return '/login'

    const publicAuthedPaths = new Set(['/home'])
    const visiblePaths = authStore.visiblePaths()
    if (!publicAuthedPaths.has(to.path) && !visiblePaths.has(to.path)) return authStore.firstVisiblePath()
  }

  return true
})

export default router

````

---

## Frontend/src/stores/authStore.ts

````text
import { defineStore } from 'pinia'
import { ref } from 'vue'

type MenuItem = {
    name: string
    path?: string // 可能是纯目录，所以 path 可选
    children?: MenuItem[]
}

const AUTH_KEY = 'smartlab_auth'

export const useAuthStore = defineStore('auth', () => {
    const isAuthenticated = ref(false)
    const username = ref('')
    const roleName = ref('')
    const userBasicInfo = ref('') // ✨ 新增：基本信息（中文身份）
    const lab = ref('')
    const avatar = ref('')
    const token = ref('')
    const menus = ref<MenuItem[]>([])
    const permissions = ref<any[]>([])
    const authObjects = ref<string[]>([])
    const menuLoaded = ref(false)

    // ✨ 新增：判断用户是否有某个权限的方法
    const normalizePermission = (permission: any) => {
        if (!permission) return ''
        if (typeof permission === 'string') return permission
        if (permission.object && permission.action) return `${permission.object}:${permission.action}`
        return ''
    }

    const hasPermission = (permission: string) => {
        if (roleName.value === 'system_admin') return true
        const normalized = new Set([
            ...authObjects.value.map(normalizePermission),
            ...permissions.value.map(normalizePermission)
        ].filter(Boolean))
        return normalized.has(permission)
    }

    const visiblePaths = () => {
        const paths = new Set<string>()
        const walk = (items: MenuItem[]) => {
            items.forEach(item => {
                if (item.path) paths.add(item.path)
                if (item.children?.length) walk(item.children)
            })
        }
        walk(menus.value)
        return paths
    }

    const firstVisiblePath = () => {
        const paths = visiblePaths()
        return paths.values().next().value || '/home'
    }

    const clearAuth = () => {
        isAuthenticated.value = false
        username.value = ''
        roleName.value = ''
        userBasicInfo.value = ''
        lab.value = ''
        avatar.value = ''
        token.value = ''
        menus.value = []
        permissions.value = []
        authObjects.value = []
        menuLoaded.value = false
        localStorage.removeItem(AUTH_KEY)
    }

    const setAuth = (userData: any, persistMenus = true) => {
        isAuthenticated.value = true
        username.value = userData.username
        roleName.value = userData.roleName
        userBasicInfo.value = userData.userBasicInfo || userData.roleName // ✨ 存储基本信息
        lab.value = userData.lab || ''
        avatar.value = userData.avatar || ''
        token.value = userData.token || ''
        if (persistMenus) {
            menus.value = userData.menus || []
            menuLoaded.value = true
        } else {
            menus.value = []
            menuLoaded.value = false
        }
        permissions.value = userData.permissions || []
        authObjects.value = userData.authObjects || []

        localStorage.setItem(AUTH_KEY, JSON.stringify({
            username: username.value,
            roleName: roleName.value,
            userBasicInfo: userBasicInfo.value,
            lab: lab.value,
            avatar: avatar.value,
            token: token.value,
            permissions: permissions.value,
            authObjects: authObjects.value
        }))
    }

    const refreshProfile = async () => {
        if (!token.value) return null
        const res = await fetch('/api/user/profile', {
            headers: { Authorization: `Bearer ${token.value}` }
        })
        if (res.status === 401) {
            clearAuth()
            return null
        }
        const body = await res.json()
        if (body?.success && body.data) {
            setAuth({ ...body.data, token: token.value })
            return body.data
        }
        return null
    }

    const initAuth = () => {
        try {
            const raw = localStorage.getItem(AUTH_KEY)
            if (!raw) return
            const saved = JSON.parse(raw)
            if (saved?.username) {
                setAuth(saved, false)
            }
        } catch {
            clearAuth()
        }
    }

    initAuth()

    return {
        isAuthenticated,
        username,
        roleName,
        userBasicInfo,
        lab,
        avatar,
        token,
        menus,
        permissions,
        authObjects,
        menuLoaded,
        clearAuth,
        setAuth,
        initAuth,
        refreshProfile,
        hasPermission,
        visiblePaths,
        firstVisiblePath
    }
})

````

---

## Frontend/src/style.css

````text
/* ==============================================================================
 * SmartLab 2.0 — Industrial Light Theme & Design System
 * ============================================================================== */

@import url('https://fonts.googleapis.com/css2?family=Outfit:wght@300;400;500;600;700;800&family=JetBrains+Mono:wght@300;400;500;600&display=swap');

:root {
  font-family: 'Outfit', 'Inter', -apple-system, sans-serif;

  /* ── Light Industrial Color Palette ── */
  --bg-primary:    #f0f2f5;
  --bg-secondary:  #ffffff;
  --bg-tertiary:   #e8eaee;

  /* Top Bar */
  --navbar-bg:     #1a2d4e;
  --navbar-border: #14243f;

  /* Sidebar / Panel */
  --panel-bg:      #ffffff;
  --panel-border:  #d9dde6;

  /* Industrial Blue — Primary Action */
  --color-primary:        #1a6bbf;
  --color-primary-dark:   #14559a;
  --color-primary-light:  #e8f1fb;
  --color-primary-rgb:    26, 107, 191;

  /* Status Colors */
  --color-success:  #1a8754;
  --color-warning:  #d97706;
  --color-danger:   #c0392b;
  --color-info:     #0891b2;

  /* Text */
  --color-text-main:  #1e2533;
  --color-text-sub:   #4a5568;
  --color-text-muted: #8a93a6;

  /* Borders & Shadows */
  --border-color:   #d0d5e0;
  --shadow-sm:      0 1px 3px rgba(0,0,0,0.08), 0 1px 2px rgba(0,0,0,0.04);
  --shadow-md:      0 4px 12px rgba(0,0,0,0.10), 0 2px 4px rgba(0,0,0,0.06);
  --shadow-lg:      0 8px 24px rgba(0,0,0,0.12), 0 4px 8px rgba(0,0,0,0.06);

  background-color: var(--bg-primary);
  color: var(--color-text-main);
  margin: 0;
  padding: 0;
  overflow-x: hidden;
  height: 100vh;
}

body {
  margin: 0;
  padding: 0;
  background-color: var(--bg-primary);
  min-height: 100vh;
}

/* ── Scrollbar ── */
::-webkit-scrollbar        { width: 6px; height: 6px; }
::-webkit-scrollbar-track  { background: var(--bg-tertiary); }
::-webkit-scrollbar-thumb  { background: #bcc3d0; border-radius: 4px; }
::-webkit-scrollbar-thumb:hover { background: var(--color-primary); }

/* ── Card ── */
.ind-card {
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.ind-card:hover {
  border-color: #b0b9cc;
  box-shadow: var(--shadow-md);
}

/* Keep glass-card as alias so components still compile */
.glass-card {
  background: var(--panel-bg);
  border: 1px solid var(--panel-border);
  border-radius: 8px;
  box-shadow: var(--shadow-sm);
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

/* ── Typography ── */
h1, h2, h3, h4, h5, h6 {
  font-weight: 600;
  letter-spacing: -0.01em;
  margin: 0 0 0.75rem 0;
  color: var(--color-text-main);
  background: none;
  -webkit-background-clip: unset;
  -webkit-text-fill-color: unset;
}

/* ── Badge ── */
.glow-badge {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 4px;
  font-size: 0.72rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border: 1px solid transparent;
}

.glow-badge.primary {
  background: #dbeafe;
  color: #1d4ed8;
  border-color: #bfdbfe;
}

.glow-badge.success {
  background: #d1fae5;
  color: #065f46;
  border-color: #a7f3d0;
}

.glow-badge.warning {
  background: #fef3c7;
  color: #92400e;
  border-color: #fde68a;
}

.glow-badge.danger {
  background: #fee2e2;
  color: #991b1b;
  border-color: #fecaca;
}

.glow-badge.info {
  background: #cffafe;
  color: #155e75;
  border-color: #a5f3fc;
}

/* ── Element Plus Overrides (Light Industrial) ── */

/* Buttons */
.el-button {
  border-radius: 6px !important;
  font-weight: 500 !important;
  transition: all 0.18s ease-in-out !important;
}

.el-button--primary {
  background: var(--color-primary) !important;
  border-color: var(--color-primary) !important;
  box-shadow: none !important;
}

.el-button--primary:hover {
  background: var(--color-primary-dark) !important;
  border-color: var(--color-primary-dark) !important;
}

/* Inputs */
.el-input__wrapper,
.el-textarea__inner {
  background-color: #ffffff !important;
  border: 1px solid var(--border-color) !important;
  box-shadow: none !important;
  border-radius: 6px !important;
}

.el-input__wrapper:hover {
  border-color: var(--color-primary) !important;
}

.el-input__wrapper.is-focus {
  border-color: var(--color-primary) !important;
  box-shadow: 0 0 0 2px rgba(26,107,191,0.15) !important;
}

/* Dialog */
.el-dialog {
  background: #ffffff !important;
  border: 1px solid var(--panel-border) !important;
  border-radius: 10px !important;
  box-shadow: var(--shadow-lg) !important;
}

.el-dialog__title {
  color: var(--color-text-main) !important;
  font-weight: 600 !important;
}

/* Table */
.el-table {
  background-color: #ffffff !important;
  --el-table-border-color: #e5e7eb !important;
  --el-table-header-bg-color: #f8f9fb !important;
  --el-table-tr-bg-color: #ffffff !important;
  --el-table-row-hover-bg-color: #f0f5ff !important;
}

.el-table th.el-table__cell {
  color: var(--color-text-sub) !important;
  font-weight: 600 !important;
  font-size: 0.82rem !important;
  text-transform: uppercase !important;
  letter-spacing: 0.04em !important;
}

.el-table__row:hover > td.el-table__cell {
  background-color: #f0f5ff !important;
}

/* Menu (top bar horizontal) */
.el-menu {
  background-color: transparent !important;
  border: none !important;
}

.el-menu--horizontal .el-menu-item {
  color: rgba(255,255,255,0.75) !important;
  border-bottom: 3px solid transparent !important;
  height: 56px !important;
  line-height: 56px !important;
  padding: 0 16px !important;
  font-size: 0.88rem !important;
  font-weight: 500 !important;
  transition: color 0.18s, border-color 0.18s, background 0.18s !important;
}

.el-menu--horizontal .el-menu-item:hover {
  color: #ffffff !important;
  background-color: rgba(255,255,255,0.08) !important;
}

.el-menu--horizontal .el-menu-item.is-active {
  color: #ffffff !important;
  border-bottom-color: #60a5fa !important;
  background-color: rgba(255,255,255,0.10) !important;
}

.el-menu--horizontal .el-sub-menu__title {
  color: rgba(255,255,255,0.75) !important;
  border-bottom: 3px solid transparent !important;
  height: 56px !important;
  line-height: 56px !important;
  padding: 0 16px !important;
  font-size: 0.88rem !important;
  font-weight: 500 !important;
}

.el-menu--horizontal .el-sub-menu__title:hover {
  color: #ffffff !important;
  background-color: rgba(255,255,255,0.08) !important;
}

.el-menu--horizontal > .el-sub-menu.is-active .el-sub-menu__title {
  color: #ffffff !important;
  border-bottom-color: #60a5fa !important;
}

/* Dropdown popup from top-bar submenu */
.el-menu--popup {
  background: #ffffff !important;
  border: 1px solid var(--panel-border) !important;
  border-radius: 8px !important;
  box-shadow: var(--shadow-lg) !important;
  padding: 4px 0 !important;
  min-width: 160px !important;
}

.el-menu--popup .el-menu-item {
  color: var(--color-text-sub) !important;
  font-size: 0.875rem !important;
  height: 40px !important;
  line-height: 40px !important;
  padding: 0 20px !important;
  border-bottom: none !important;
}

.el-menu--popup .el-menu-item:hover {
  background-color: var(--color-primary-light) !important;
  color: var(--color-primary) !important;
}

.el-menu--popup .el-menu-item.is-active {
  background-color: var(--color-primary-light) !important;
  color: var(--color-primary) !important;
  font-weight: 600 !important;
}

.el-card {
  border: 1px solid var(--panel-border) !important;
  background: #ffffff !important;
  border-radius: 8px !important;
  box-shadow: var(--shadow-sm) !important;
}

/* Select & Form */
.el-select .el-input__wrapper {
  background-color: #ffffff !important;
}

.el-form-item__label {
  color: var(--color-text-sub) !important;
  font-weight: 500 !important;
}

/* Tag */
.el-tag {
  border-radius: 4px !important;
}

/* Message Box */
.el-message-box {
  border-radius: 10px !important;
  border: 1px solid var(--panel-border) !important;
}

````

---

## Frontend/src/utils/request.js

````text
import { useAuthStore } from '../stores/authStore'
import { ElMessage } from 'element-plus'

// 封装一个带有 Token 的 fetch 方法
export const request = async (url, options = {}) => {
  const authStore = useAuthStore()
  
  // 1. 设置默认 Headers，并携带 Token
  const headers = {
    'Content-Type': 'application/json',
    ...options.headers,
  }
  
  // 核心：如果本地有 Token，就塞进 Authorization 请求头里
  if (authStore.token) {
    headers['Authorization'] = `Bearer ${authStore.token}`
  }

  // 2. 发起请求
  try {
    const response = await fetch(url, { ...options, headers })
    
    // 3. 处理 401 未授权情况（Token过期或无效）
    if (response.status === 401) {
      authStore.clearAuth()
      ElMessage.error('登录已过期，请重新登录')
      window.location.href = '/login' // 强制跳回登录页
      throw new Error('Unauthorized')
    }

    const data = await response.json()
    return data;
  } catch (error) {
    throw error
  }
}
````

---

## Frontend/src/views/admin/UserManagement.vue

````text
<template>
  <div class="user-page">
    <el-card shadow="never" class="page-card">
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <el-button type="primary" size="small" @click="openRegisterDialog">新增用户</el-button>
        </div>
      </template>

      <el-table :data="users" border stripe size="small" v-loading="loadingUsers">
        <el-table-column prop="id" label="用户编号" width="90" />
        <el-table-column prop="userName" label="用户名" min-width="140" />
        <el-table-column label="角色" min-width="120">
          <template #default="{ row }">{{ row.roleName || '-' }}</template>
        </el-table-column>
        <el-table-column prop="lab" label="实验室" min-width="120" />
        <el-table-column label="说明" min-width="160">
          <template #default="{ row }">{{ row.userBasicInfo?.description || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPermissionDialog(row)">分配特权</el-button>
            <el-button link type="primary" @click="viewTaskHistory(row)">任务记录</el-button>
            <el-popconfirm title="确认删除该用户？" @confirm="deleteUser(row.id)">
              <template #reference>
                <el-button link type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="registerDialogVisible" title="注册用户" width="460px">
      <el-form :model="registerForm" :rules="registerRules" ref="registerFormRef" label-width="100px" size="small">
        <el-form-item label="用户名" prop="userName"><el-input v-model="registerForm.userName" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="registerForm.password" type="password" show-password /></el-form-item>
        <el-form-item label="角色" prop="roleName">
          <el-select v-model="registerForm.roleName" style="width: 100%">
            <el-option label="系统管理员" value="system_admin" />
            <el-option label="实验室管理员" value="lab_admin" />
            <el-option label="研究员" value="researcher" />
            <el-option label="观察员" value="observer" />
          </el-select>
        </el-form-item>
        <el-form-item label="实验室" prop="lab"><el-input v-model="registerForm.lab" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="registerForm.description" placeholder="例如：有机实验组" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="registerDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="submitRegister">确认</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="permissionDrawerVisible" :title="`分配特权：${currentUser?.userName || ''}`" size="420px">
      <div v-loading="loadingPermissions">
        <el-checkbox-group v-model="selectedPermissionIds" class="perm-list">
          <el-checkbox
            v-for="perm in permissionDict"
            :key="perm.id"
            :label="perm.id"
            border
            class="perm-item"
          >
            {{ perm.object }} / {{ perm.action }}
          </el-checkbox>
        </el-checkbox-group>
        <div class="drawer-actions">
          <el-button type="primary" :loading="savingPermissions" @click="savePermissions">保存</el-button>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="historyDrawerVisible" :title="`用户任务记录：${currentUser?.userName || ''}`" size="60%">
      <el-table :data="userHistory" border stripe size="small" v-loading="loadingHistory">
        <el-table-column prop="taskId" label="任务编号" width="100" />
        <el-table-column prop="taskName" label="任务名称" min-width="180" />
        <el-table-column prop="templateId" label="流程" min-width="120" />
        <el-table-column prop="currentStatus" label="状态" width="100" />
        <el-table-column label="开始时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
        </el-table-column>
        <el-table-column label="结束时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.endTime) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'

const users = ref<any[]>([])
const loadingUsers = ref(false)

const registerDialogVisible = ref(false)
const registerFormRef = ref<FormInstance>()
const registering = ref(false)
const registerForm = ref({
  userName: '',
  password: '',
  roleName: 'researcher',
  lab: '',
  description: ''
})

const registerRules: FormRules = {
  userName: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  roleName: [{ required: true, message: '请选择角色', trigger: 'change' }],
  lab: [{ required: true, message: '请输入实验室', trigger: 'blur' }]
}

const permissionDrawerVisible = ref(false)
const currentUser = ref<any>(null)
const permissionDict = ref<any[]>([])
const selectedPermissionIds = ref<number[]>([])
const loadingPermissions = ref(false)
const savingPermissions = ref(false)

const historyDrawerVisible = ref(false)
const userHistory = ref<any[]>([])
const loadingHistory = ref(false)

const fetchUsers = async () => {
  loadingUsers.value = true
  try {
    const res = await axios.get('/api/user/list')
    if (res.data?.success) {
      users.value = res.data.data || []
    } else {
      ElMessage.error(res.data?.message || '加载用户失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载用户失败')
  } finally {
    loadingUsers.value = false
  }
}

const openRegisterDialog = () => {
  registerForm.value = { userName: '', password: '', roleName: 'researcher', lab: '', description: '' }
  registerDialogVisible.value = true
}

const submitRegister = async () => {
  if (!registerFormRef.value) return
  await registerFormRef.value.validate(async valid => {
    if (!valid) return
    registering.value = true
    try {
      const payload = {
        userName: registerForm.value.userName,
        password: registerForm.value.password,
        roleName: registerForm.value.roleName,
        lab: registerForm.value.lab,
        userBasicInfo: { description: registerForm.value.description || registerForm.value.roleName }
      }
      const res = await axios.post('/api/user/register', payload)
      if (res.data?.success) {
        ElMessage.success('用户注册成功')
        registerDialogVisible.value = false
        await fetchUsers()
      } else {
        ElMessage.error(res.data?.message || '注册失败')
      }
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message || '注册失败')
    } finally {
      registering.value = false
    }
  })
}

const deleteUser = async (id: number) => {
  try {
    const res = await axios.delete(`/api/user/delete/${id}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      await fetchUsers()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '删除失败')
  }
}

const openPermissionDialog = async (user: any) => {
  currentUser.value = user
  permissionDrawerVisible.value = true
  loadingPermissions.value = true
  try {
    const [dictRes, userRes] = await Promise.all([
      axios.get('/api/user/permissions/list'),
      axios.get(`/api/user/permissions/by-user/${user.id}`)
    ])
    if (dictRes.data?.success) permissionDict.value = dictRes.data.data || []
    if (userRes.data?.success) selectedPermissionIds.value = userRes.data.data || []
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载权限失败')
  } finally {
    loadingPermissions.value = false
  }
}

const savePermissions = async () => {
  if (!currentUser.value) return
  savingPermissions.value = true
  try {
    const res = await axios.post('/api/user/permissions/assign', {
      userId: currentUser.value.id,
      permissionIds: selectedPermissionIds.value
    })
    if (res.data?.success) {
      ElMessage.success('特权分配成功')
      permissionDrawerVisible.value = false
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '保存失败')
  } finally {
    savingPermissions.value = false
  }
}

const viewTaskHistory = async (user: any) => {
  currentUser.value = user
  historyDrawerVisible.value = true
  loadingHistory.value = true
  try {
    const res = await axios.get(`/api/user/task-history/${user.id}`)
    if (res.data?.success) {
      userHistory.value = res.data.data || []
    } else {
      ElMessage.error(res.data?.message || '加载任务记录失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载任务记录失败')
  } finally {
    loadingHistory.value = false
  }
}

const formatTime = (time?: string) => (time ? new Date(time).toLocaleString('zh-CN', { hour12: false }) : '-')

onMounted(fetchUsers)
</script>

<style scoped>
.user-page { padding: 16px; background: #f2f4f7; min-height: calc(100vh - 52px); }
.page-card { border: 1px solid #d8dce3; }
.card-header { display: flex; justify-content: space-between; align-items: center; }
.perm-list { display: flex; flex-direction: column; gap: 8px; }
.perm-item { margin-right: 0 !important; }
.drawer-actions { display: flex; justify-content: flex-end; margin-top: 12px; }
</style>

````

---

## Frontend/src/views/auth/Login.vue

````text
<template>
  <div class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <h1 class="logo-text">SMARTLAB</h1>
        <p class="subtitle">科研装备协同控制控制台</p>
      </div>
      <el-form :model="form" @keyup.enter="handleLogin" size="large">
        <el-form-item>
          <el-input v-model="form.userName" placeholder="用户名" prefix-icon="User" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" prefix-icon="Lock" />
        </el-form-item>
        <el-button type="primary" :loading="loading" class="w-full submit-btn" @click="handleLogin">登录</el-button>
        <div class="link-wrap">
          <router-link to="/register">没有账号？立即注册</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../../stores/authStore'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const authStore = useAuthStore()
const form = ref({ userName: '', password: '' })
const loading = ref(false)

const handleLogin = async () => {
  if (!form.value.userName || !form.value.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const res = await axios.post('/api/user/login', form.value)
    if (res.data.success) {
      const { token, user } = res.data.data
      authStore.setAuth({
        username: user.username,
        roleName: user.roleName,
        userBasicInfo: user.userBasicInfo,
        lab: user.lab,
        token: token,
        menus: user.menus,
        permissions: user.permissions,
        authObjects: user.authObjects
      })
      ElMessage.success('登录成功')
      // 先用 Vue Router 进行正常跳转，在跳转完成后进行页面重载以确保全局配置最新
      router.push(authStore.firstVisiblePath()).then(() => {
        window.location.reload()
      })
    } else {
      ElMessage.error(res.data.message || '登录失败')
    }
  } catch (err) {
    ElMessage.error(err.response?.data?.message || '网络异常，请检查后端连接')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  font-family: 'Inter', -apple-system, sans-serif;
}
.auth-card {
  width: 380px;
  background: rgba(255, 255, 255, 0.95);
  padding: 40px 32px;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
}
.auth-header {
  text-align: center;
  margin-bottom: 32px;
}
.logo-text {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: 2px;
  background: linear-gradient(to right, #2563eb, #3b82f6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0 0 8px 0;
}
.subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}
.w-full {
  width: 100%;
}
.submit-btn {
  background-color: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
  transition: all 0.2s ease;
}
.submit-btn:hover {
  background-color: #1d4ed8;
  border-color: #1d4ed8;
}
.link-wrap {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;
}
.link-wrap a {
  color: #2563eb;
  text-decoration: none;
  font-weight: 500;
}
.link-wrap a:hover {
  text-decoration: underline;
}
</style>

````

---

## Frontend/src/views/auth/Register.vue

````text
<template>
  <div class="auth-container">
    <div class="auth-card">
      <div class="auth-header">
        <h1 class="logo-text">SMARTLAB</h1>
        <p class="subtitle">注册新账户</p>
      </div>
      <el-form :model="form" @keyup.enter="handleRegister" size="large">
        <el-form-item>
          <el-input v-model="form.userName" placeholder="用户名" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" show-password placeholder="密码" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.lab" placeholder="实验室名称" />
        </el-form-item>
        <el-form-item>
          <el-select v-model="form.roleName" placeholder="分配角色" class="w-full">
            <el-option label="系统管理员" value="system_admin" />
            <el-option label="实验室管理员" value="lab_admin" />
            <el-option label="研究员" value="researcher" />
            <el-option label="观察员" value="observer" />
          </el-select>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="w-full submit-btn" @click="handleRegister">注册</el-button>
        <div class="link-wrap">
          <router-link to="/login">返回登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const router = useRouter()
const form = ref({ userName: '', password: '', lab: '', roleName: 'researcher' })
const loading = ref(false)

const handleRegister = async () => {
  if (!form.value.userName || !form.value.password || !form.value.lab) {
    ElMessage.warning('请填写完整信息')
    return
  }
  loading.value = true
  try {
    const res = await axios.post('/api/user/register', form.value)
    if (res.data.success) {
      ElMessage.success('注册成功')
      router.push('/login')
    } else {
      ElMessage.error(res.data.message || '注册失败')
    }
  } catch (err) {
    ElMessage.error('网络异常，请检查后端连接')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  font-family: 'Inter', -apple-system, sans-serif;
}
.auth-card {
  width: 380px;
  background: rgba(255, 255, 255, 0.95);
  padding: 40px 32px;
  border-radius: 12px;
  box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.3), 0 10px 10px -5px rgba(0, 0, 0, 0.2);
  border: 1px solid rgba(255, 255, 255, 0.1);
  backdrop-filter: blur(10px);
}
.auth-header {
  text-align: center;
  margin-bottom: 32px;
}
.logo-text {
  font-size: 32px;
  font-weight: 800;
  letter-spacing: 2px;
  background: linear-gradient(to right, #2563eb, #3b82f6);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0 0 8px 0;
}
.subtitle {
  font-size: 14px;
  color: #64748b;
  margin: 0;
}
.w-full {
  width: 100%;
}
.submit-btn {
  background-color: #2563eb;
  border-color: #2563eb;
  font-weight: 600;
  transition: all 0.2s ease;
}
.submit-btn:hover {
  background-color: #1d4ed8;
  border-color: #1d4ed8;
}
.link-wrap {
  text-align: center;
  margin-top: 20px;
  font-size: 13px;
}
.link-wrap a {
  color: #2563eb;
  text-decoration: none;
  font-weight: 500;
}
.link-wrap a:hover {
  text-decoration: underline;
}
</style>
````

---

## Frontend/src/views/dashboard/Dashboard.vue

````text
<template>
  <div class="dashboard-container">
    <div class="header-row">
      <h1 class="page-title">首页</h1>
      <div class="header-actions">
        <el-tag :type="mqttStatusTagType" class="mqtt-tag">MQTT {{ mqttStatusLabel }}</el-tag>
        <el-tag type="info" class="time-tag">{{ currentTime }}</el-tag>
      </div>
    </div>

    <el-alert
      v-if="showMqttAlert"
      class="mqtt-alert"
      type="warning"
      show-icon
      :closable="false"
      :title="mqttAlertTitle"
    />

    <el-row :gutter="16" class="stats-row">
      <el-col :xs="12" :md="6"><div class="stat-card"><span>设备总数</span><strong>{{ stats.totalDevices || 0 }}</strong></div></el-col>
      <el-col :xs="12" :md="6"><div class="stat-card"><span>在线设备</span><strong>{{ stats.onlineDevices || 0 }}</strong></div></el-col>
      <el-col :xs="12" :md="6"><div class="stat-card"><span>任务总数</span><strong>{{ stats.totalTasks || 0 }}</strong></div></el-col>
      <el-col :xs="12" :md="6"><div class="stat-card"><span>进行中任务</span><strong>{{ stats.runningTasks || 0 }}</strong></div></el-col>
    </el-row>

    <el-row :gutter="16" class="main-row">
      <el-col :xs="24" :lg="8">
        <el-card shadow="never" class="panel-card" header="实验室级约束">
          <el-form label-position="top">
            <el-form-item label="最大并发任务数">
              <el-input-number v-model="labConstraint.maxConcurrentTasks" :min="1" :max="100" style="width: 100%" />
            </el-form-item>
            <el-form-item label="允许夜间执行">
              <el-switch v-model="labConstraint.allowNightOperations" active-text="允许" inactive-text="禁止" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" style="width: 100%" @click="saveLabConstraint">保存约束</el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card shadow="never" class="panel-card" header="实验室场景概览">
          <div class="scene-list">
            <div class="scene-item">数据模板：{{ stats.totalTemplates || 0 }}</div>
            <div class="scene-item">数据记录：{{ stats.totalDataRecords || 0 }}</div>
            <div class="scene-item">离线设备：{{ stats.offlineDevices || 0 }}</div>
            <div class="scene-item">失败任务：{{ stats.failedTasks || 0 }}</div>
          </div>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="16">
        <el-card shadow="never" class="panel-card" header="进行中的任务">
          <el-table :data="runningTasks" size="small" border stripe>
            <el-table-column prop="taskId" label="任务编号" width="90" />
            <el-table-column prop="taskName" label="任务名称" min-width="140" />
            <el-table-column prop="templateId" label="流程" min-width="120" />
            <el-table-column prop="startTime" label="开始时间" min-width="140">
              <template #default="{ row }">{{ formatTime(row.startTime) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card shadow="never" class="panel-card" header="设备实时状态">
          <el-row :gutter="12">
            <el-col :xs="24" :sm="12" :md="8" v-for="device in devices" :key="device.instanceId" style="margin-bottom: 12px;">
              <div class="device-card" @click="openDeviceDrawer(device)">
                <div class="device-name">{{ device.instanceName }}</div>
                <div class="device-meta">{{ getModelLabel(device.modelId) }}</div>
                <el-tag size="small" :type="device.isOnline ? 'success' : 'info'">{{ device.isOnline ? '在线' : '离线' }}</el-tag>
              </div>
            </el-col>
          </el-row>
        </el-card>
      </el-col>
    </el-row>

    <el-drawer v-model="deviceDrawerVisible" :title="`设备详情：${currentDevice?.instanceName || ''}`" size="520px">
      <div v-if="currentDevice">
        <el-descriptions :column="1" border size="small" class="mb-12">
          <el-descriptions-item label="设备实例">{{ currentDevice.instanceId }}</el-descriptions-item>
          <el-descriptions-item label="设备模型">{{ currentDevice.modelId }}</el-descriptions-item>
          <el-descriptions-item label="位置">{{ currentDevice.commConfig?.location || '-' }}</el-descriptions-item>
          <el-descriptions-item label="MQTT 主题">{{ currentDevice.commConfig?.mqttTopic || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">实时状态</el-divider>
        <el-descriptions :column="1" border size="small" class="mb-12">
          <el-descriptions-item label="指令状态">{{ liveSnapshot?.currentCommandState || '-' }}</el-descriptions-item>
          <el-descriptions-item label="功能状态">{{ liveSnapshot?.currentOperationState || '-' }}</el-descriptions-item>
          <el-descriptions-item label="实时属性">
            <div v-if="liveSnapshot?.latestAttributes">
              <el-tag
                v-for="(val, key) in liveSnapshot.latestAttributes"
                :key="key"
                size="small"
                type="info"
                class="mr-8 mb-8"
              >{{ key }}: {{ val }}</el-tag>
            </div>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">设备约束</el-divider>
        <el-table :data="deviceConstraints" size="small" border>
          <el-table-column label="属性">
            <template #default="{ row }">
              <el-select v-model="row.targetAttr" style="width: 100%" placeholder="选择属性">
                <el-option v-for="attr in currentDeviceAttrs" :key="attr.identifier" :label="attr.name" :value="attr.identifier" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="条件" width="100">
            <template #default="{ row }">
              <el-select v-model="row.operator" style="width: 100%">
                <el-option label=">" value=">" />
                <el-option label="<" value="<" />
                <el-option label=">=" value=">=" />
                <el-option label="<=" value="<=" />
                <el-option label="=" value="==" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="阈值" width="120">
            <template #default="{ row }"><el-input v-model="row.threshold" /></template>
          </el-table-column>
          <el-table-column label="操作" width="64">
            <template #default="{ $index }">
              <el-button link type="danger" @click="removeConstraint($index)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="drawer-actions">
          <el-button @click="addConstraint">新增约束</el-button>
          <el-button type="primary" :loading="savingConstraint" @click="saveDeviceConstraint">保存约束</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const currentTime = ref('')
let timer: number | undefined
let mqttTimer: number | undefined

const stats = ref<any>({})
const devices = ref<any[]>([])
const runningTasks = ref<any[]>([])
const modelMap = ref<Record<string, any>>({})
const mqttStatus = ref<any>({ enabled: true, status: 'NOT_STARTED', connected: false })

const labConstraint = ref({
  maxConcurrentTasks: Number(localStorage.getItem('lab:maxConcurrentTasks') || 10),
  allowNightOperations: localStorage.getItem('lab:allowNightOperations') === 'true'
})

const deviceDrawerVisible = ref(false)
const currentDevice = ref<any>(null)
const liveSnapshot = ref<any>(null)
const deviceConstraints = ref<Array<{ targetAttr: string; operator: string; threshold: string }>>([])
const savingConstraint = ref(false)

const currentDeviceAttrs = computed(() => {
  if (!currentDevice.value?.modelId) return []
  return modelMap.value[currentDevice.value.modelId]?.capabilitySpec?.attributes || []
})

const mqttStatusLabel = computed(() => {
  if (mqttStatus.value?.enabled === false) return '未启用'
  if (mqttStatus.value?.connected) return '已连接'
  const status = mqttStatus.value?.status || 'UNKNOWN'
  if (status === 'CONNECTING') return '连接中'
  if (status === 'NOT_STARTED') return '未启动'
  return '未连接'
})

const mqttStatusTagType = computed(() => {
  if (mqttStatus.value?.enabled === false) return 'info'
  return mqttStatus.value?.connected ? 'success' : 'warning'
})

const showMqttAlert = computed(() => mqttStatus.value?.enabled !== false && mqttStatus.value?.connected === false)

const mqttAlertTitle = computed(() => {
  const broker = mqttStatus.value?.broker || '未配置 broker'
  const topic = mqttStatus.value?.registerTopic || 'smartlab/adapter/register'
  const reason = mqttStatus.value?.lastError ? '，原因：' + mqttStatus.value.lastError : ''
  return 'MQTT 未连接：' + broker + '。系统已正常启动，正在后台重试；当前待订阅注册话题：' + topic + reason
})

const updateTime = () => {
  currentTime.value = new Date().toLocaleString('zh-CN', { hour12: false })
}

const formatTime = (v?: string) => (v ? new Date(v).toLocaleString('zh-CN', { hour12: false }) : '-')

const getModelLabel = (modelId: string) => {
  const model = modelMap.value[modelId]
  return model ? model.modelName : modelId
}

const fetchMqttStatus = async () => {
  try {
    const res = await axios.get('/api/adapter/protocol/mqtt/status')
    if (res.data?.success) {
      mqttStatus.value = res.data.data || { status: 'UNKNOWN', connected: false }
    }
  } catch (err: any) {
    mqttStatus.value = { enabled: true, status: 'UNKNOWN', connected: false, lastError: err?.response?.data?.message || '无法获取 MQTT 状态' }
  }
}

const fetchData = async () => {
  try {
    const [deviceRes, modelRes, taskSummaryRes, runningTaskRes, templateRes, dataIndexRes] = await Promise.all([
      axios.get('/api/device/instance/list'),
      axios.get('/api/device/model/list'),
      axios.get('/api/task/summary'),
      axios.get('/api/task/page', { params: { pageNo: 1, pageSize: 10, status: 'RUNNING' } }),
      axios.get('/api/data/template/list'),
      axios.get('/api/data/index/list')
    ])
    const responses = [deviceRes, modelRes, taskSummaryRes, runningTaskRes, templateRes, dataIndexRes]
    const failed = responses.find(res => !res.data?.success)
    if (failed) {
      ElMessage.error(failed.data?.message || '加载首页数据失败')
      return
    }
    const deviceList = deviceRes.data.data || []
    const modelList = modelRes.data.data || []
    const taskSummary = taskSummaryRes.data.data || {}
    const runningTaskPage = runningTaskRes.data.data || {}
    const templateList = templateRes.data.data || []
    const dataIndexList = dataIndexRes.data.data || []

    const onlineDevices = deviceList.filter((device: any) => device.isOnline || device.onlineStatus === 'ONLINE').length
    stats.value = {
      totalDevices: deviceList.length,
      onlineDevices,
      offlineDevices: Math.max(0, deviceList.length - onlineDevices),
      totalTasks: taskSummary.total || 0,
      runningTasks: taskSummary.running || 0,
      failedTasks: taskSummary.failed || 0,
      totalTemplates: templateList.length,
      totalDataRecords: dataIndexList.length
    }
    devices.value = deviceList
    runningTasks.value = runningTaskPage.records || []
    const map: Record<string, any> = {}
    modelList.forEach((m: any) => (map[m.modelId] = m))
    modelMap.value = map
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载首页数据失败')
  }
}

const saveLabConstraint = () => {
  localStorage.setItem('lab:maxConcurrentTasks', String(labConstraint.value.maxConcurrentTasks))
  localStorage.setItem('lab:allowNightOperations', String(labConstraint.value.allowNightOperations))
  ElMessage.success('实验室约束已保存')
}

const openDeviceDrawer = async (device: any) => {
  currentDevice.value = JSON.parse(JSON.stringify(device))
  const constraints = currentDevice.value?.commConfig?.constraints
  deviceConstraints.value = Array.isArray(constraints) ? constraints : []
  liveSnapshot.value = null
  deviceDrawerVisible.value = true
  try {
    const res = await axios.get(`/api/device/instance/snapshot/${device.instanceId}`)
    if (res.data?.success) liveSnapshot.value = res.data.data
  } catch {}
}

const addConstraint = () => {
  deviceConstraints.value.push({ targetAttr: '', operator: '>', threshold: '' })
}

const removeConstraint = (idx: number) => {
  deviceConstraints.value.splice(idx, 1)
}

const saveDeviceConstraint = async () => {
  if (!currentDevice.value) return
  savingConstraint.value = true
  try {
    const payload = JSON.parse(JSON.stringify(currentDevice.value))
    if (!payload.commConfig) payload.commConfig = {}
    payload.commConfig.constraints = deviceConstraints.value.filter(c => c.targetAttr && c.threshold)
    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('设备约束已保存')
      await fetchData()
      deviceDrawerVisible.value = false
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '保存失败')
  } finally {
    savingConstraint.value = false
  }
}

onMounted(() => {
  updateTime()
  timer = window.setInterval(updateTime, 1000)
  fetchData()
  fetchMqttStatus()
  mqttTimer = window.setInterval(fetchMqttStatus, 10000)
})

onUnmounted(() => {
  if (timer) window.clearInterval(timer)
  if (mqttTimer) window.clearInterval(mqttTimer)
})
</script>

<style scoped>
.dashboard-container { padding: 16px; background: #f2f4f7; min-height: calc(100vh - 52px); }
.header-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; gap: 12px; }
.header-actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.mqtt-alert { margin-bottom: 12px; }
.mqtt-tag { font-weight: 600; }
.page-title { margin: 0; font-size: 20px; color: #1f2937; }
.time-tag { font-family: 'JetBrains Mono', monospace; }
.stats-row { margin-bottom: 12px; }
.stat-card { background: #fff; border: 1px solid #d8dce3; border-radius: 6px; padding: 12px; display: flex; flex-direction: column; gap: 4px; }
.stat-card span { font-size: 12px; color: #6b7280; }
.stat-card strong { font-size: 22px; color: #111827; }
.main-row { margin-top: 0; }
.panel-card { border: 1px solid #d8dce3; margin-bottom: 12px; }
.scene-list { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.scene-item { background: #f6f7f9; border: 1px solid #e5e7eb; border-radius: 4px; padding: 8px; font-size: 12px; color: #374151; }
.device-card { border: 1px solid #d8dce3; background: #fff; border-radius: 6px; padding: 10px; cursor: pointer; display: flex; flex-direction: column; gap: 4px; }
.device-card:hover { border-color: #9aa4b2; }
.device-name { font-size: 13px; color: #1f2937; font-weight: 600; }
.device-meta { font-size: 12px; color: #6b7280; }
.drawer-actions { display: flex; justify-content: flex-end; gap: 8px; margin-top: 12px; }
.mb-12 { margin-bottom: 12px; }
.mr-8 { margin-right: 8px; }
</style>

````

---

## Frontend/src/views/data/DataManagement.vue

````text
<template>
  <div class="data-workbench">
    <aside class="asset-pane">
      <div class="asset-header">
        <div>
          <strong>数据中心</strong>
          <span>设备数据 / 模板库</span>
        </div>
        <el-button size="small" type="primary" @click="openTemplateDrawer">新增模板</el-button>
      </div>
      <el-input v-model="keyword" size="small" clearable placeholder="搜索设备、模型、数据表" class="asset-search" />
      <div class="asset-tree" v-loading="loading">
        <el-tree :data="filteredTree" node-key="key" default-expand-all :expand-on-click-node="false" @node-click="handleTreeClick">
          <template #default="{ data }">
            <div class="asset-node" :class="{ active: activeKey === data.key, dataset: data.type === 'dataset', template: data.type === 'template' }">
              <span class="asset-node-main">
                <el-icon v-if="['root','category'].includes(data.type)"><Folder /></el-icon>
                <el-icon v-else-if="['model','instance'].includes(data.type)"><Cpu /></el-icon>
                <el-icon v-else><Tickets /></el-icon>
                <span>{{ data.label }}</span>
              </span>
              <em v-if="data.count != null">{{ data.count }}</em>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!filteredTree.length" description="暂无数据资产" />
      </div>
    </aside>

    <section class="data-workspace">
      <div class="workspace-toolbar">
        <div>
          <span>{{ workspaceTag }}</span>
          <h1>{{ workspaceTitle }}</h1>
        </div>
        <div class="toolbar-actions">
          <el-button @click="loadAll">刷新</el-button>
          <el-button v-if="selectedTemplate" type="primary" @click="openDatasetDrawer(selectedTemplate)">用模板建表</el-button>
          <el-button v-if="selectedInstance" type="primary" @click="openDatasetDrawer(null, selectedInstance)">为该设备建表</el-button>
          <el-button v-if="selectedDataset" @click="exportDataset">导出 CSV</el-button>
          <el-popconfirm v-if="selectedDataset" title="确认删除该数据表？物理表会同步删除" @confirm="deleteDataset(selectedDataset)">
            <template #reference><el-button type="danger" plain>删除数据表</el-button></template>
          </el-popconfirm>
          <el-popconfirm v-if="selectedTemplate && !selectedTemplate.isDefault" title="确认删除该模板？已有数据表时后端会阻止删除" @confirm="deleteTemplate(selectedTemplate)">
            <template #reference><el-button type="danger" plain>删除模板</el-button></template>
          </el-popconfirm>
        </div>
      </div>

      <main class="workspace-body">
        <section v-if="selectedDataset" class="workspace-section dataset-section">
          <div class="meta-table">
            <div><span>数据表</span><strong>{{ selectedDataset.dataTable || '-' }}</strong></div>
            <div><span>绑定设备</span><strong>{{ instanceName(selectedDataset.deviceInstanceId) }}</strong></div>
            <div><span>数据模板</span><strong>{{ templateName(selectedDataset.dataTemplateId) }}</strong></div>
            <div><span>创建时间</span><strong>{{ formatTime(selectedDataset.createTime) }}</strong></div>
          </div>
          <div ref="chartRef" class="chart-box"></div>
          <div class="table-title"><strong>历史数据</strong><span>{{ recordPage.total }} 条</span></div>
          <el-table :data="records" border stripe size="small" v-loading="loadingRecords" class="record-table">
            <el-table-column label="采集时间" width="180"><template #default="{ row }">{{ formatTime(recordTime(row)) }}</template></el-table-column>
            <el-table-column v-for="field in valueFields" :key="field.columnName" :label="fieldLabel(field)" min-width="150">
              <template #default="{ row }">{{ valueOf(row, field.columnName) }}</template>
            </el-table-column>
          </el-table>
          <el-pagination class="pager" background small layout="total, sizes, prev, pager, next" :total="recordPage.total" :current-page="recordPage.pageNo" :page-size="recordPage.pageSize" :page-sizes="[50,100,200,500]" @current-change="page => { recordPage.pageNo = page; loadRecords() }" @size-change="size => { recordPage.pageNo = 1; recordPage.pageSize = size; loadRecords() }" />
        </section>

        <section v-else-if="selectedTemplate" class="workspace-section">
          <div class="meta-table">
            <div><span>模板名称</span><strong>{{ selectedTemplate.templateName }}</strong></div>
            <div><span>来源模型</span><strong>{{ modelName(selectedTemplate.deviceModelId) }}</strong></div>
            <div><span>模板类型</span><strong>{{ selectedTemplate.isDefault ? '默认模板' : '自定义模板' }}</strong></div>
            <div><span>创建时间</span><strong>{{ formatTime(selectedTemplate.createTime) }}</strong></div>
          </div>
          <div class="table-title"><strong>模板字段</strong><span>{{ templateDetails.length }} 个字段</span></div>
          <el-table :data="templateDetails" border stripe size="small" class="template-table">
            <el-table-column prop="columnName" label="模板字段" min-width="160" />
            <el-table-column prop="columnDesc" label="字段说明" min-width="160" />
            <el-table-column label="绑定属性" min-width="150"><template #default="{ row }">{{ bindingLabel(row) }}</template></el-table-column>
            <el-table-column label="默认值" width="130"><template #default="{ row }">{{ row.defaultValue || '-' }}</template></el-table-column>
            <el-table-column label="字段用途" width="120"><template #default="{ row }">{{ isUnitField(row) ? '单位' : '采集值' }}</template></el-table-column>
          </el-table>
        </section>

        <section v-else-if="selectedInstance" class="workspace-section">
          <div class="meta-table">
            <div><span>设备实例</span><strong>{{ selectedInstance.instanceName }}</strong></div>
            <div><span>所属模型</span><strong>{{ modelName(selectedInstance.deviceModelId) }}</strong></div>
            <div><span>绑定 Adapter</span><strong>{{ selectedInstance.boundAdapterName || '-' }}</strong></div>
            <div><span>设备点位</span><strong>{{ selectedInstance.boundDevicePoint || '-' }}</strong></div>
          </div>
          <el-empty description="该设备实例下暂无数据表"><el-button type="primary" @click="openDatasetDrawer(null, selectedInstance)">为该设备建表</el-button></el-empty>
        </section>

        <section v-else class="workspace-empty"><el-empty description="请选择左侧数据表、模板或设备实例" /></section>
      </main>
    </section>

    <el-drawer v-model="datasetDrawer.visible" title="新建数据表" size="520px">
      <el-form label-position="top" class="drawer-form">
        <el-form-item label="数据表说明"><el-input v-model="datasetDrawer.dataDesc" placeholder="例如：高压报警专项数据" /></el-form-item>
        <el-form-item label="数据模板"><el-select v-model="datasetDrawer.templateId" filterable placeholder="请选择模板"><el-option v-for="tpl in templates" :key="tpl.id" :label="tpl.templateName" :value="tpl.id" /></el-select></el-form-item>
        <el-form-item label="绑定设备实例"><el-select v-model="datasetDrawer.deviceInstanceId" filterable placeholder="请选择设备"><el-option v-for="ins in instances" :key="instanceId(ins)" :label="instancePath(ins)" :value="Number(instanceId(ins))" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button @click="datasetDrawer.visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="createDataset">保存建表</el-button></template>
    </el-drawer>

    <el-drawer v-model="templateDrawer.visible" title="新增数据模板" size="680px">
      <el-form label-position="top" class="drawer-form">
        <div class="form-grid two">
          <el-form-item label="模板名称"><el-input v-model="templateDrawer.templateName" /></el-form-item>
          <el-form-item label="绑定模型"><el-select v-model="templateDrawer.deviceModelId" filterable @change="generateFieldsFromModel"><el-option v-for="model in models" :key="modelId(model)" :label="model.modelName" :value="Number(modelId(model))" /></el-select></el-form-item>
        </div>
        <el-form-item label="模板说明"><el-input v-model="templateDrawer.templateDesc" /></el-form-item>
        <div class="table-title"><strong>模板字段</strong><el-button size="small" @click="addTemplateField">新增字段</el-button></div>
        <div class="field-editor">
          <div class="field-head"><span>模板字段</span><span>字段说明</span><span>绑定属性</span><span>默认值</span><span></span></div>
          <div v-for="(field, idx) in templateDrawer.details" :key="field._key" class="field-row">
            <el-input v-model="field.columnName" />
            <el-input v-model="field.columnDesc" />
            <el-select v-model="field.deviceAttrKey" clearable placeholder="可为空"><el-option v-for="attr in selectedTemplateModelAttrs" :key="attr.name" :label="attr.displayName || attr.name" :value="attr.name" /></el-select>
            <el-input v-model="field.defaultValue" placeholder="无默认值" />
            <el-button text type="danger" @click="templateDrawer.details.splice(idx, 1)">删除</el-button>
          </div>
        </div>
      </el-form>
      <template #footer><el-button @click="templateDrawer.visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="saveTemplate">保存模板</el-button></template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, shallowRef, watch } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Cpu, Folder, Tickets } from '@element-plus/icons-vue'
import * as echarts from 'echarts'

const loading = ref(false)
const loadingRecords = ref(false)
const saving = ref(false)
const keyword = ref('')
const activeKey = ref('')
const categories = ref([])
const models = ref([])
const instances = ref([])
const datasets = ref([])
const templates = ref([])
const templateDetails = ref([])
const records = ref([])
const selectedDataset = ref(null)
const selectedTemplate = ref(null)
const selectedInstance = ref(null)
const chartRef = ref(null)
const chart = shallowRef(null)
const recordPage = reactive({ pageNo: 1, pageSize: 100, total: 0 })
const detailCache = reactive({})

const datasetDrawer = reactive({ visible: false, templateId: null, deviceInstanceId: null, dataDesc: '' })
const templateDrawer = reactive({ visible: false, templateName: '', templateDesc: '', deviceModelId: null, details: [] })

const modelMap = computed(() => Object.fromEntries(models.value.map(m => [String(modelId(m)), m])))
const templateMap = computed(() => Object.fromEntries(templates.value.map(t => [String(t.id), t])))
const childrenByCategory = computed(() => {
  const map = new Map()
  categories.value.forEach(cat => {
    const parent = cat.parentCategoryId == null ? 'root' : String(cat.parentCategoryId)
    if (!map.has(parent)) map.set(parent, [])
    map.get(parent).push(cat)
  })
  return map
})
const selectedTemplateModelAttrs = computed(() => asArray(modelMap.value[String(templateDrawer.deviceModelId)]?.attributes))
const valueFields = computed(() => templateDetails.value.filter(row => !isUnitField(row)))
const unitMap = computed(() => {
  const map = new Map()
  templateDetails.value.filter(isUnitField).forEach(row => {
    map.set(stripUnit(row.columnName), row.defaultValue || '')
    if (row.deviceAttrKey) map.set(stripUnit(row.deviceAttrKey), row.defaultValue || '')
  })
  return map
})
const workspaceTag = computed(() => selectedDataset.value ? '数据表' : selectedTemplate.value ? '数据模板' : selectedInstance.value ? '设备实例' : '数据资产')
const workspaceTitle = computed(() => selectedDataset.value?.dataDesc || selectedDataset.value?.dataTable || selectedTemplate.value?.templateName || selectedInstance.value?.instanceName || '请选择数据对象')
const assetTree = computed(() => [
  { key: 'root_device_data', type: 'root', label: '设备数据', count: datasets.value.length, children: buildCategoryNodes('root') },
  { key: 'root_templates', type: 'root', label: '模板库', count: templates.value.length, children: templates.value.map(t => ({ key: 'template_' + t.id, type: 'template', label: t.templateName, data: t, count: t.isDefault ? '默认' : '自定义' })) }
])
const filteredTree = computed(() => filterTree(assetTree.value, keyword.value.trim().toLowerCase()))

async function loadAll() {
  loading.value = true
  try {
    const [catRes, modelRes, insRes, datasetRes, tplRes] = await Promise.all([
      axios.get('/api/device/category/list').catch(() => ({ data: { data: [] } })),
      axios.get('/api/device/model/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/data/index/list'),
      axios.get('/api/data/template/list')
    ])
    categories.value = asArray(catRes.data?.data)
    models.value = asArray(modelRes.data?.data)
    instances.value = asArray(insRes.data?.data)
    datasets.value = asArray(datasetRes.data?.data)
    templates.value = asArray(tplRes.data?.data)
    if (!selectedDataset.value && !selectedTemplate.value && !selectedInstance.value) selectFirstAvailable()
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载数据中心失败'))
  } finally {
    loading.value = false
  }
}

function buildCategoryNodes(parentKey) {
  return asArray(childrenByCategory.value.get(parentKey)).map(cat => {
    const modelNodes = models.value.filter(m => String(m.categoryId) === String(cat.id)).map(model => buildModelNode(model))
    return { key: 'category_' + cat.id, type: 'category', label: cat.categoryName, data: cat, count: modelNodes.length, children: [...buildCategoryNodes(String(cat.id)), ...modelNodes] }
  }).filter(node => node.children.length > 0 || node.count > 0)
}

function buildModelNode(model) {
  const modelInstances = instances.value.filter(ins => String(ins.deviceModelId || ins.modelId) === String(modelId(model)))
  return { key: 'model_' + modelId(model), type: 'model', label: model.modelName, data: model, count: modelInstances.length, children: modelInstances.map(buildInstanceNode) }
}

function buildInstanceNode(instance) {
  const rows = datasets.value.filter(ds => String(ds.deviceInstanceId) === String(instanceId(instance)))
  return { key: 'instance_' + instanceId(instance), type: 'instance', label: instance.instanceName, data: instance, count: rows.length, children: rows.map(ds => ({ key: 'dataset_' + ds.id, type: 'dataset', label: ds.dataDesc || ds.dataTable, data: ds })) }
}

function filterTree(nodes, kw) {
  if (!kw) return nodes
  return nodes.map(node => {
    const children = filterTree(asArray(node.children), kw)
    const hit = String(node.label || '').toLowerCase().includes(kw)
    return hit || children.length ? { ...node, children } : null
  }).filter(Boolean)
}

async function handleTreeClick(node) {
  activeKey.value = node.key
  if (node.type === 'dataset') return selectDataset(node.data)
  if (node.type === 'template') return selectTemplate(node.data)
  if (node.type === 'instance') return selectInstance(node.data)
}

async function selectDataset(dataset) {
  selectedDataset.value = dataset
  selectedTemplate.value = null
  selectedInstance.value = instances.value.find(ins => String(instanceId(ins)) === String(dataset.deviceInstanceId)) || null
  activeKey.value = 'dataset_' + dataset.id
  recordPage.pageNo = 1
  await loadTemplateDetails(dataset.dataTemplateId)
  await loadRecords()
}

async function selectTemplate(template) {
  selectedTemplate.value = template
  selectedDataset.value = null
  selectedInstance.value = null
  activeKey.value = 'template_' + template.id
  records.value = []
  disposeChart()
  await loadTemplateDetails(template.id)
}

function selectInstance(instance) {
  selectedInstance.value = instance
  selectedDataset.value = null
  selectedTemplate.value = null
  records.value = []
  templateDetails.value = []
  disposeChart()
}

async function loadTemplateDetails(templateId) {
  if (!templateId) { templateDetails.value = []; return }
  if (detailCache[templateId]) { templateDetails.value = detailCache[templateId]; return }
  const res = await axios.get(`/api/data/template/${templateId}/details`)
  detailCache[templateId] = asArray(res.data?.data)
  templateDetails.value = detailCache[templateId]
}

async function loadRecords() {
  if (!selectedDataset.value) return
  loadingRecords.value = true
  try {
    const res = await axios.get(`/api/data/record/dataset/${selectedDataset.value.id}`, { params: { pageNo: recordPage.pageNo, pageSize: recordPage.pageSize } })
    const page = res.data?.data || {}
    records.value = asArray(page.records)
    recordPage.total = Number(page.total || 0)
    await nextTick()
    renderChart()
  } catch (err) {
    ElMessage.error(errorMessage(err, '加载数据记录失败'))
  } finally {
    loadingRecords.value = false
  }
}

function renderChart() {
  if (!chartRef.value || !selectedDataset.value) return
  if (!chart.value) chart.value = echarts.init(chartRef.value)
  const sorted = [...records.value].sort((a, b) => new Date(recordTime(a) || 0) - new Date(recordTime(b) || 0))
  const xData = sorted.map(row => formatTime(recordTime(row)))
  const units = [...new Set(valueFields.value.map(field => unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'))]
  const yAxis = units.map((unit, idx) => ({ type: 'value', name: unit, position: idx % 2 ? 'right' : 'left', offset: idx > 1 ? (idx - 1) * 42 : 0, splitLine: { show: idx === 0 } }))
  const series = valueFields.value.map(field => {
    const unit = unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey) || '数值'
    return { name: field.columnDesc || field.columnName, type: 'line', smooth: true, yAxisIndex: Math.max(0, units.indexOf(unit)), data: sorted.map(row => numericOrNull(valueOf(row, field.columnName))) }
  })
  chart.value.setOption({ tooltip: { trigger: 'axis' }, legend: { top: 8 }, grid: { left: 56, right: Math.max(24, units.length * 42), top: 54, bottom: 36 }, xAxis: { type: 'category', data: xData }, yAxis: yAxis.length ? yAxis : [{ type: 'value' }], series }, true)
}

function openDatasetDrawer(template = null, instance = null) {
  datasetDrawer.visible = true
  datasetDrawer.templateId = template?.id || selectedDataset.value?.dataTemplateId || selectedTemplate.value?.id || null
  if (instance) datasetDrawer.deviceInstanceId = Number(instanceId(instance))
  else if (selectedDataset.value?.deviceInstanceId) datasetDrawer.deviceInstanceId = Number(selectedDataset.value.deviceInstanceId)
  else if (selectedInstance.value) datasetDrawer.deviceInstanceId = Number(instanceId(selectedInstance.value))
  else datasetDrawer.deviceInstanceId = null
  datasetDrawer.dataDesc = ''
}

async function createDataset() {
  if (!datasetDrawer.templateId || !datasetDrawer.deviceInstanceId) { ElMessage.warning('请选择模板和设备实例'); return }
  saving.value = true
  try {
    const res = await axios.post('/api/data/index/create-dataset', { templateId: datasetDrawer.templateId, deviceInstanceId: datasetDrawer.deviceInstanceId, dataDesc: datasetDrawer.dataDesc })
    if (res.data?.success) {
      ElMessage.success('数据表已创建')
      datasetDrawer.visible = false
      await loadAll()
      if (res.data.data?.id) await selectDataset(res.data.data)
    } else ElMessage.error(res.data?.message || '创建失败')
  } catch (err) { ElMessage.error(errorMessage(err, '创建失败')) } finally { saving.value = false }
}

function openTemplateDrawer() {
  Object.assign(templateDrawer, { visible: true, templateName: '', templateDesc: '', deviceModelId: null, details: [] })
}

function generateFieldsFromModel() {
  const model = modelMap.value[String(templateDrawer.deviceModelId)]
  const attrs = asArray(model?.attributes)
  templateDrawer.details = attrs.flatMap(attr => {
    const name = attr.name || attr.displayName
    const desc = attr.displayName || attr.name
    const rows = [{ _key: uid(), columnName: name, columnDesc: desc, propertyTypeId: propertyTypeId(attr.dataType), columnLength: 255, deviceAttrKey: name, defaultValue: '' }]
    if (attr.unit) rows.push({ _key: uid(), columnName: `${name}_unit`, columnDesc: `${desc}单位`, propertyTypeId: 6, columnLength: 50, deviceAttrKey: '', defaultValue: attr.unit })
    return rows
  })
}

function addTemplateField() { templateDrawer.details.push({ _key: uid(), columnName: '', columnDesc: '', propertyTypeId: 6, columnLength: 255, deviceAttrKey: '', defaultValue: '' }) }

async function saveTemplate() {
  if (!templateDrawer.templateName.trim()) { ElMessage.warning('请输入模板名称'); return }
  if (!templateDrawer.deviceModelId) { ElMessage.warning('请选择绑定模型'); return }
  saving.value = true
  try {
    const payload = { templateName: templateDrawer.templateName, templateDesc: templateDrawer.templateDesc, deviceModelId: templateDrawer.deviceModelId, isDefault: false, details: templateDrawer.details.map(({ _key, ...row }) => ({ ...row, columnLength: row.columnLength || 255 })) }
    const res = await axios.post('/api/data/template/save', payload)
    if (res.data?.success) {
      ElMessage.success('模板已保存')
      templateDrawer.visible = false
      await loadAll()
    } else ElMessage.error(res.data?.message || '保存失败')
  } catch (err) { ElMessage.error(errorMessage(err, '保存失败')) } finally { saving.value = false }
}

async function deleteDataset(dataset) {
  const res = await axios.delete(`/api/data/index/delete/${dataset.id}`)
  if (res.data?.success) { ElMessage.success('数据表已删除'); selectedDataset.value = null; records.value = []; await loadAll() } else ElMessage.error(res.data?.message || '删除失败')
}

async function deleteTemplate(template) {
  const res = await axios.delete(`/api/data/template/delete/${template.id}`)
  if (res.data?.success) { ElMessage.success('模板已删除'); selectedTemplate.value = null; templateDetails.value = []; delete detailCache[template.id]; await loadAll() } else ElMessage.error(res.data?.message || '删除失败')
}

function exportDataset() { if (selectedDataset.value?.id) window.open(`/api/data/record/export/${selectedDataset.value.id}`, '_blank') }
function selectFirstAvailable() { const firstDataset = datasets.value[0]; if (firstDataset) selectDataset(firstDataset); else if (templates.value[0]) selectTemplate(templates.value[0]) }
function fieldLabel(field) { const unit = unitMap.value.get(field.columnName) || unitMap.value.get(field.deviceAttrKey); return `${field.columnDesc || field.columnName}${unit ? ' (' + unit + ')' : ''}` }
function bindingLabel(row) { if (isUnitField(row)) return '-'; return row.deviceAttrKey || '-' }
function valueOf(row, key) { const value = row?.[key] ?? row?.[String(key).toLowerCase()] ?? row?.data?.[key] ?? row?.payload?.[key]; return value == null || value === '' ? '-' : value }
function numericOrNull(value) { const n = Number(value); return Number.isFinite(n) ? n : null }
function recordTime(row) { return row?.create_time || row?.createTime || row?.timestamp || row?.collectTime }
function formatTime(value) { return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-' }
function isUnitField(row) { return String(row?.columnName || '').endsWith('_unit') }
function stripUnit(value) { return String(value || '').replace(/_unit$/, '') }
function modelId(model) { return model?.modelId || model?.id }
function instanceId(instance) { return instance?.instanceId || instance?.id }
function modelName(id) { return modelMap.value[String(id)]?.modelName || '-' }
function templateName(id) { return templateMap.value[String(id)]?.templateName || '-' }
function instanceName(id) { return instances.value.find(ins => String(instanceId(ins)) === String(id))?.instanceName || '-' }
function instancePath(ins) { return `${modelName(ins.deviceModelId)} / ${ins.instanceName}` }
function asArray(value) { return Array.isArray(value) ? value : [] }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function propertyTypeId(type) { const t = String(type || '').toUpperCase(); return t === 'DOUBLE' ? 4 : t === 'INTEGER' ? 3 : t === 'BOOLEAN' ? 5 : 6 }
function uid() { return Math.random().toString(36).slice(2, 10) }
function disposeChart() { chart.value?.dispose(); chart.value = null }
function resizeChart() { chart.value?.resize() }

watch(templateDetails, () => nextTick(renderChart))
onMounted(() => { loadAll(); window.addEventListener('resize', resizeChart) })
onUnmounted(() => { window.removeEventListener('resize', resizeChart); disposeChart() })
</script>

<style scoped>
.data-workbench { height: calc(100vh - 52px); display: flex; background: #eef2f6; color: #0f172a; }
.asset-pane { width: 360px; min-width: 360px; background: #f8fafc; border-right: 1px solid #cbd5e1; display: flex; flex-direction: column; }
.asset-header { min-height: 58px; padding: 10px 12px; background: #fff; border-bottom: 1px solid #dbe3ee; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.asset-header strong { display: block; font-size: 16px; }
.asset-header span { color: #64748b; font-size: 12px; }
.asset-search { width: auto; margin: 10px 12px; }
.asset-tree { flex: 1; min-height: 0; overflow: auto; padding: 4px 8px 12px; }
.asset-node { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.asset-node-main { min-width: 0; display: flex; align-items: center; gap: 6px; }
.asset-node-main span { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.asset-node em { min-width: 26px; min-height: 18px; border-radius: 9px; background: #e2e8f0; color: #475569; display: inline-flex; align-items: center; justify-content: center; font-size: 11px; font-style: normal; }
.asset-node.active .asset-node-main span { color: #1d4ed8; font-weight: 800; }
.asset-node.dataset .asset-node-main span { color: #047857; }
.asset-node.template .asset-node-main span { color: #6d28d9; }
.data-workspace { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.workspace-toolbar { min-height: 72px; padding: 10px 16px; background: #fff; border-bottom: 1px solid #cbd5e1; display: flex; justify-content: space-between; align-items: center; gap: 12px; }
.workspace-toolbar span { color: #64748b; font-size: 12px; }
.workspace-toolbar h1 { margin: 2px 0 0; font-size: 24px; line-height: 1.25; }
.toolbar-actions { display: flex; flex-wrap: wrap; gap: 8px; justify-content: flex-end; }
.workspace-body { flex: 1; min-height: 0; overflow: auto; padding: 10px 12px 22px; }
.workspace-section { background: #fff; border: 1px solid #cbd5e1; padding: 12px; }
.meta-table { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); border: 1px solid #dbe3ee; margin-bottom: 10px; }
.meta-table div { min-width: 0; padding: 9px 10px; border-right: 1px solid #e2e8f0; background: #f8fafc; }
.meta-table div:last-child { border-right: 0; }
.meta-table span { display: block; color: #64748b; font-size: 12px; margin-bottom: 3px; }
.meta-table strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }
.chart-box { height: 320px; border: 1px solid #dbe3ee; margin-bottom: 10px; }
.table-title { min-height: 36px; display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.table-title strong { font-size: 16px; }
.table-title span { color: #64748b; font-size: 12px; }
.record-table, .template-table { width: 100%; }
.pager { margin-top: 10px; display: flex; justify-content: flex-end; }
.workspace-empty { min-height: 480px; display: flex; align-items: center; justify-content: center; background: #fff; border: 1px solid #cbd5e1; }
.drawer-form { padding: 0 18px; }
.form-grid.two { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.field-editor { border: 1px solid #dbe3ee; }
.field-head, .field-row { display: grid; grid-template-columns: minmax(130px, 1fr) minmax(130px, 1fr) minmax(130px, 1fr) minmax(110px, 0.8fr) 60px; align-items: center; gap: 8px; padding: 8px; border-bottom: 1px solid #e2e8f0; }
.field-head { background: #f1f5f9; font-weight: 800; color: #334155; }
.field-row:last-child { border-bottom: 0; }
@media (max-width: 1180px) { .meta-table { grid-template-columns: repeat(2, minmax(0, 1fr)); } .field-head, .field-row { grid-template-columns: 1fr; } }
</style>

````

---

## Frontend/src/views/device/AdapterManagement.vue

````text
<template>
  <div class="adapter-management-page">
    <header class="page-toolbar">
      <div class="toolbar-title">
        <h1>设备执行代理</h1>
        <span>Adapter 注册、连接状态、设备点与实例绑定</span>
      </div>
      <div class="toolbar-actions">
        <el-input v-model="keyword" placeholder="搜索 Adapter" clearable :prefix-icon="Search" />
        <el-button :icon="Connection" :loading="mqttLoading" @click="reconnectMqtt">重连 MQTT</el-button>
        <el-button :icon="Refresh" @click="fetchData">刷新</el-button>
        <el-button type="primary" :icon="Plus" @click="openRegisterDrawer">注册 Adapter</el-button>
      </div>
    </header>

    <main class="adapter-workspace">
      <aside class="adapter-sidebar">
        <section class="mqtt-strip">
          <span class="strip-label">MQTT Broker</span>
          <strong>{{ mqttStatusLabel }}</strong>
          <el-tag :type="mqttConnected ? 'success' : 'danger'" effect="plain" size="small">{{ mqttConnected ? '已连接' : '未连接' }}</el-tag>
        </section>

        <div class="list-title">
          <strong>Adapter 列表</strong>
          <em>{{ filteredAdapters.length }} 个</em>
        </div>
        <div class="adapter-list" v-loading="loading">
          <el-empty v-if="filteredAdapters.length === 0" description="暂无 Adapter" :image-size="88" />
          <button
            v-for="item in filteredAdapters"
            :key="adapterIdOf(item)"
            type="button"
            class="adapter-list-item"
            :class="{ active: activeKey === adapterIdOf(item) }"
            @click="activeKey = adapterIdOf(item)"
          >
            <span class="adapter-name">{{ item.adapterName || '未命名 Adapter' }}</span>
            <span class="adapter-subline">{{ categoryCount(item) }} 类别 / {{ templateCount(item) }} 模板 / {{ pointCount(item) }} 点位</span>
            <span class="adapter-status" :class="statusClass(item.status)">{{ statusLabel(item.status) }}</span>
          </button>
        </div>
      </aside>

      <section v-if="activeAdapter" class="adapter-detail">
        <div class="detail-header">
          <div>
            <span>Adapter</span>
            <h2>{{ activeAdapter.adapterName }}</h2>
          </div>
          <div class="detail-actions">
            <el-button type="danger" plain :icon="Delete" @click="deleteAdapter(activeAdapter)">删除</el-button>
          </div>
        </div>

        <section class="runtime-table">
          <div><span>运行状态</span><strong>{{ statusLabel(activeAdapter.status) }}</strong></div>
          <div><span>最后心跳</span><strong>{{ formatTime(activeAdapter.lastHeartbeat) }}</strong></div>
          <div><span>设备模板</span><strong>{{ activeTemplates.length }}</strong></div>
          <div><span>设备点位</span><strong>{{ activePoints.length }}</strong></div>
          <div><span>绑定实例</span><strong>{{ boundInstances.length }}</strong></div>
        </section>

        <el-tabs v-model="activeTab" class="adapter-tabs">
          <el-tab-pane label="模板与点位" name="runtime">
            <section class="content-block">
              <div class="block-head"><h3>设备模板 / 设备点</h3><em>{{ activeTemplates.length }} 模板 / {{ activePoints.length }} 点位</em></div>
              <el-table
                :data="templatePointTree"
                row-key="id"
                border
                size="small"
                default-expand-all
                class="industrial-table relation-table"
                :tree-props="{ children: 'children' }"
              >
                <el-table-column label="对象" min-width="220">
                  <template #default="{ row }">
                    <div class="relation-name">
                      <el-tag size="small" :type="row.nodeType === 'category' ? 'warning' : (row.nodeType === 'template' ? 'primary' : 'success')" effect="plain">
                        {{ row.nodeType === 'category' ? '类别' : (row.nodeType === 'template' ? '模板' : '点位') }}
                      </el-tag>
                      <strong>{{ row.label }}</strong>
                      <span v-if="row.nodeType === 'point'">{{ row.devicePoint }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="模板标识" min-width="140">
                  <template #default="{ row }">{{ row.templateName || '-' }}</template>
                </el-table-column>
                <el-table-column label="模板能力" min-width="220">
                  <template #default="{ row }">
                    <span v-if="row.nodeType === 'template' || row.nodeType === 'category'">{{ row.summary }}</span>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
                <el-table-column label="点位编号" width="100">
                  <template #default="{ row }">{{ row.nodeType === 'point' ? (row.index ?? '-') : '-' }}</template>
                </el-table-column>
                <el-table-column label="属性点映射" min-width="260">
                  <template #default="{ row }">
                    <div v-if="row.nodeType === 'point'" class="mapping-chips">
                      <span v-for="(point, attr) in row.attributeMapping || {}" :key="attr"><b>{{ attr }}</b><i>→</i>{{ point }}</span>
                      <em v-if="!Object.keys(row.attributeMapping || {}).length">-</em>
                    </div>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="命令与事件" name="contract">
            <section class="content-block">
              <div class="block-head"><h3>Adapter 命令</h3><em>{{ activeCommands.length }} 条</em></div>
              <el-table :data="activeCommands" border stripe size="small" class="industrial-table">
                <el-table-column label="命令名" min-width="170">
                  <template #default="{ row }"><strong>{{ row.name || row.commandName }}</strong></template>
                </el-table-column>
                <el-table-column label="说明" min-width="180">
                  <template #default="{ row }">{{ row.description || '-' }}</template>
                </el-table-column>
                <el-table-column label="命令参数" min-width="360">
                  <template #default="{ row }">
                    <div class="param-table-list">
                      <span v-for="param in visibleCommandParams(row)" :key="param.name || param.paramName">
                        <b>{{ param.name || param.paramName }}</b><em>{{ param.dataType || '-' }}</em>
                      </span>
                      <i v-if="visibleCommandParams(row).length === 0">无外部参数</i>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </section>

            <section class="content-block">
              <div class="block-head"><h3>Adapter 事件</h3><em>{{ activeEvents.length }} 个</em></div>
              <el-table :data="activeEvents" border stripe size="small" class="industrial-table">
                <el-table-column label="事件名" min-width="200" prop="name" />
                <el-table-column label="事件类型" width="120" prop="type" />
                <el-table-column label="说明" min-width="220" prop="description" />
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="绑定实例" name="bindings">
            <section class="content-block">
              <div class="block-head"><h3>设备实例绑定</h3><em>{{ boundInstances.length }} 台</em></div>
              <el-tree
                v-if="bindingTreeData.length"
                :data="bindingTreeData"
                node-key="id"
                default-expand-all
                :expand-on-click-node="false"
                class="binding-tree"
              >
                <template #default="{ data }">
                  <div class="binding-node" :class="data.type">
                    <span class="binding-label">{{ data.label }}</span>
                    <span class="binding-meta">{{ data.meta }}</span>
                  </div>
                </template>
              </el-tree>
              <el-empty v-else description="暂无实例绑定" :image-size="80" />
            </section>
          </el-tab-pane>

          <el-tab-pane label="配置快照" name="json">
            <section class="content-block json-block">
              <div class="block-head"><h3>解析配置</h3></div>
              <pre>{{ activeConfigText }}</pre>
            </section>
          </el-tab-pane>
        </el-tabs>
      </section>

      <section v-else class="adapter-detail empty-detail">
        <el-empty description="请选择或注册 Adapter" :image-size="110" />
      </section>
    </main>

    <el-drawer v-model="registerDrawerVisible" title="注册 Adapter" size="640px" append-to-body>
      <section class="register-flow-card">
        <div class="flow-head">
          <div>
            <span>注册监听主题</span>
            <strong>smartlab/adapter/register</strong>
          </div>
          <el-tag :type="mqttConnected ? 'success' : 'danger'" effect="plain">{{ mqttConnected ? 'MQTT 已连接' : 'MQTT 未连接' }}</el-tag>
        </div>
        <div class="flow-actions">
          <el-button :icon="Connection" :loading="mqttLoading" @click="reconnectMqtt">连接 / 重连 MQTT</el-button>
          <el-button :icon="Refresh" :loading="pendingLoading" @click="fetchPendingRegistrations">刷新待确认</el-button>
        </div>
      </section>

      <section class="pending-register-panel" v-loading="pendingLoading">
        <div class="drawer-section-head">
          <h3>待确认注册</h3>
          <span>{{ pendingRegistrations.length }} 个</span>
        </div>
        <el-empty v-if="pendingRegistrations.length === 0" description="启动 Adapter 后，系统会在这里显示它发布的注册请求" :image-size="96" />
        <el-table v-else :data="pendingRegistrations" border size="small" class="industrial-table">
          <el-table-column label="Adapter" min-width="190">
            <template #default="{ row }"><strong>{{ row.adapterName }}</strong></template>
          </el-table-column>
          <el-table-column label="收到时间" min-width="160">
            <template #default="{ row }">{{ formatTime(row.receivedAt) }}</template>
          </el-table-column>
          <el-table-column label="类别 / 模板 / 点位" min-width="150">
            <template #default="{ row }">{{ pendingCategoryCount(row) }} / {{ row.templateCount || asArray(row.parsedConfig?.deviceTemplates).length }} / {{ row.devicePointCount || asArray(row.parsedConfig?.devicePoints).length }}</template>
          </el-table-column>
          <el-table-column label="格式" width="90">
            <template #default="{ row }">{{ row.rawConfigFormat || 'JSON' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" :loading="registerLoading" @click="completePendingRegistration(row)">完成注册</el-button>
              <el-button size="small" plain @click="reviewPendingRegistration(row)">审阅</el-button>
              <el-button size="small" type="danger" link @click="discardPendingRegistration(row)">忽略</el-button>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section v-if="registerPreview" class="register-review-panel">
        <div class="drawer-section-head">
          <h3>配置审阅</h3>
          <span>{{ registerPreview.adapterName || selectedPendingAdapterName || '-' }}</span>
        </div>
        <el-form label-width="96px" size="small" class="review-form">
          <el-form-item label="Adapter 说明">
            <el-input v-model="registerPreview.adapterDescription" placeholder="用于管理页面显示，不影响 Adapter 底层协议" />
          </el-form-item>
        </el-form>
        <el-table :data="adapterCategoriesOf(registerPreview)" border size="small" class="industrial-table review-table">
          <el-table-column label="设备类别" min-width="150">
            <template #default="{ row }"><strong>{{ row.categoryName || '-' }}</strong></template>
          </el-table-column>
          <el-table-column label="类别说明" min-width="190">
            <template #default="{ row }"><el-input v-model="row.categoryDescription" size="small" placeholder="类别说明" /></template>
          </el-table-column>
          <el-table-column label="设备模板" min-width="150">
            <template #default="{ row }">{{ row.deviceTemplate?.templateName || row.deviceTemplate?.name || '-' }}</template>
          </el-table-column>
          <el-table-column label="模板说明" min-width="210">
            <template #default="{ row }"><el-input v-model="row.deviceTemplate.description" size="small" placeholder="模板说明" /></template>
          </el-table-column>
          <el-table-column label="能力摘要" min-width="220">
            <template #default="{ row }">{{ asArray(row.deviceTemplate?.attributes).length }} 属性 / {{ asArray(row.deviceTemplate?.commands).length }} 命令 / {{ eventCount(row.deviceTemplate?.events) }} 事件 / {{ asArray(row.devicePoints).length }} 点位</template>
          </el-table-column>
        </el-table>
        <div class="review-actions">
          <el-button type="primary" :disabled="!selectedPendingAdapterName" :loading="registerLoading" @click="completeReviewedRegistration">按当前审阅完成注册</el-button>
        </div>
      </section>

      <details class="manual-register-panel">
        <summary>手动导入配置</summary>
        <el-form label-width="96px" size="small" class="register-form">
          <el-form-item label="Adapter 名称">
            <el-input v-model="registerForm.adapterName" placeholder="可为空，解析配置后自动带出" />
          </el-form-item>
          <el-form-item label="配置格式">
            <el-select v-model="registerForm.rawConfigFormat">
              <el-option label="JSON" value="JSON" />
              <el-option label="INI" value="INI" />
              <el-option label="YAML" value="YAML" />
              <el-option label="XML" value="XML" />
            </el-select>
          </el-form-item>
          <el-form-item label="配置文件">
            <div class="upload-row">
              <el-upload :auto-upload="false" :show-file-list="false" accept=".json,.txt,.ini,.yaml,.yml,.xml" :on-change="importRegisterFile">
                <el-button :icon="Upload">上传配置</el-button>
              </el-upload>
              <el-button plain @click="parseRegisterConfig">解析预览</el-button>
            </div>
          </el-form-item>
          <el-form-item label="配置内容">
            <el-input v-model="registerForm.rawConfigContent" type="textarea" :rows="8" placeholder="粘贴 adapter-manifest.json 或 AdapterRegisterRequest.rawConfigContent" />
          </el-form-item>
        </el-form>

        <section v-if="registerPreview" class="preview-box">
          <div><span>Adapter</span><strong>{{ registerPreview.adapterName }}</strong></div>
          <div><span>类别</span><strong>{{ adapterCategoriesOf(registerPreview).length }}</strong></div>
          <div><span>点位</span><strong>{{ adapterPointsOf(registerPreview).length }}</strong></div>
        </section>

        <div class="manual-actions">
          <el-button plain :loading="registerLoading" @click="parseRegisterConfig">解析</el-button>
          <el-button type="primary" :loading="registerLoading" @click="registerAdapter">保存注册</el-button>
        </div>
      </details>

      <template #footer>
        <div class="drawer-footer">
          <el-button @click="registerDrawerVisible = false">关闭</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Connection, Delete, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'

const keyword = ref('')
const activeTab = ref('runtime')
const activeKey = ref('')
const loading = ref(false)
const mqttLoading = ref(false)
const adapters = ref([])
const instances = ref([])
const models = ref({})
const mqttStatus = ref(null)
const registerDrawerVisible = ref(false)
const registerLoading = ref(false)
const pendingLoading = ref(false)
const pendingRegistrations = ref([])
const registerPreview = ref(null)
const selectedPendingAdapterName = ref('')
const registerForm = reactive({ adapterName: '', rawConfigFormat: 'JSON', rawConfigContent: '' })
let registrationStream = null

const fetchData = async () => {
  loading.value = true
  try {
    const [adapterRes, instanceRes, modelRes] = await Promise.all([
      axios.get('/api/adapter/index/list'),
      axios.get('/api/device/instance/list'),
      axios.get('/api/device/model/list')
    ])
    adapters.value = adapterRes.data?.success ? asArray(adapterRes.data.data) : []
    instances.value = instanceRes.data?.success ? asArray(instanceRes.data.data) : []
    if (modelRes.data?.success) {
      const map = {}
      asArray(modelRes.data.data).forEach(model => {
        map[String(model.modelId || model.id)] = model.modelName || model.name || String(model.modelId || model.id)
      })
      models.value = map
    }
    if (!adapters.value.some(item => adapterIdOf(item) === activeKey.value)) {
      activeKey.value = adapters.value[0] ? adapterIdOf(adapters.value[0]) : ''
    }
  } finally {
    loading.value = false
  }
  fetchMqttStatus()
}

const fetchMqttStatus = async () => {
  try {
    const res = await getMqttStatus()
    mqttStatus.value = res.data?.data || res.data || null
  } catch (error) {
    mqttStatus.value = { connected: false, status: 'UNAVAILABLE' }
  }
}

const getMqttStatus = async () => {
  try {
    return await axios.get('/api/adapter/protocol/mqtt/status')
  } catch (error) {
    return axios.get('/api/adapter/mqtt/mqtt/status')
  }
}

const reconnectMqtt = async () => {
  mqttLoading.value = true
  try {
    try {
      await axios.post('/api/adapter/protocol/mqtt/reconnect')
    } catch (error) {
      await axios.post('/api/adapter/mqtt/mqtt/reconnect')
    }
    ElMessage.success('已发起 MQTT 重连')
    await fetchMqttStatus()
  } finally {
    mqttLoading.value = false
  }
}

const filteredAdapters = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return adapters.value
  return adapters.value.filter(item => String(item.adapterName || '').toLowerCase().includes(kw))
})

const activeAdapter = computed(() => adapters.value.find(item => adapterIdOf(item) === activeKey.value) || null)
const activeConfig = computed(() => parsedConfigOf(activeAdapter.value))
const activeCategories = computed(() => adapterCategoriesOf(activeConfig.value))
const activeTemplates = computed(() => activeCategories.value.map(category => ({
  ...category.deviceTemplate,
  categoryName: category.categoryName,
  categoryDescription: category.categoryDescription
})))
const activePoints = computed(() => adapterPointsOf(activeConfig.value))
const activeCommands = computed(() => activeTemplates.value.flatMap(tpl => asArray(tpl.commands)))
const activeEvents = computed(() => activeTemplates.value.flatMap(tpl => eventList(tpl.events)))
const activeConfigText = computed(() => JSON.stringify(activeConfig.value || {}, null, 2))
const templatePointTree = computed(() => activeCategories.value.map(category => {
  const template = category.deviceTemplate || {}
  const templateName = template.templateName || template.name || category.categoryName || '\u672a\u547d\u540d\u6a21\u677f'
  const categoryName = category.categoryName || templateName
  const pointChildren = asArray(category.devicePoints).map(point => ({
    ...point,
    id: `point:${categoryName}:${point.devicePoint}`,
    nodeType: 'point',
    label: point.description || point.devicePoint || '\u672a\u547d\u540d\u70b9\u4f4d',
    templateName
  }))
  return {
    id: `category:${categoryName}`,
    nodeType: 'category',
    label: category.categoryDescription || categoryName,
    templateName,
    summary: `${asArray(template.attributes).length} 属性 / ${asArray(template.commands).length} 命令 / ${eventCount(template.events)} 事件`,
    children: [{
      id: `template:${categoryName}:${templateName}`,
      nodeType: 'template',
      label: template.description || templateName,
      templateName,
      summary: `${asArray(template.attributes).length} 属性 / ${asArray(template.commands).length} 命令 / ${eventCount(template.events)} 事件`,
      children: pointChildren
    }]
  }
}))
const boundInstances = computed(() => {
  const name = activeAdapter.value?.adapterName
  if (!name) return []
  return instances.value.filter(instance => boundAdapterOf(instance) === name)
})
const bindingTreeData = computed(() => {
  const groups = new Map()
  boundInstances.value.forEach(instance => {
    const modelId = String(instance.modelId || instance.deviceModelId || '')
    if (!groups.has(modelId)) {
      groups.set(modelId, {
        id: `model:${modelId}`,
        type: 'model',
        label: `\u8bbe\u5907\u6a21\u578b:${modelNameOf(modelId)}`,
        meta: modelId ? `\u6a21\u578bID ${modelId}` : '\u672a\u7ed1\u5b9a\u6a21\u578b',
        children: []
      })
    }
    const point = boundDevicePointOf(instance) || '\u672a\u7ed1\u5b9a\u70b9\u4f4d'
    groups.get(modelId).children.push({
      id: `instance:${instance.instanceId || instance.id || instance.instanceName}`,
      type: 'instance',
      label: instance.instanceName || '\u672a\u547d\u540d\u5b9e\u4f8b',
      meta: `\u5b9e\u4f8bID ${instance.instanceId || instance.id || '-'}\uff0c\u70b9\u4f4d ${point}`,
      children: [{ id: `point:${instance.instanceId || instance.id}:${point}`, type: 'point', label: `\u7ed1\u5b9a\u70b9\u4f4d:${point}`, meta: activeAdapter.value?.adapterName || '-' }]
    })
  })
  return Array.from(groups.values())
})
const mqttConnected = computed(() => mqttStatus.value?.connected === true || mqttStatus.value?.available === true || String(mqttStatus.value?.status || '').toUpperCase() === 'CONNECTED')
const mqttStatusLabel = computed(() => mqttConnected.value ? 'Broker \u5728\u7ebf' : 'Broker \u672a\u8fde\u63a5')


const connectRegistrationStream = () => {
  if (registrationStream) return
  try {
    registrationStream = new EventSource('/api/adapter/protocol/pending-registrations/stream')
    registrationStream.addEventListener('pending_snapshot', event => {
      pendingRegistrations.value = asArray(JSON.parse(event.data || '[]'))
    })
    registrationStream.addEventListener('adapter_register_request', event => {
      const item = JSON.parse(event.data || '{}')
      if (!item?.adapterName) return
      const index = pendingRegistrations.value.findIndex(row => row.adapterName === item.adapterName)
      if (index >= 0) pendingRegistrations.value.splice(index, 1, item)
      else pendingRegistrations.value.unshift(item)
      selectedPendingAdapterName.value = item.adapterName
      registerPreview.value = cloneJson(item.parsedConfig)
    })
    registrationStream.onerror = () => {
      closeRegistrationStream()
    }
  } catch (error) {
    registrationStream = null
  }
}

const closeRegistrationStream = () => {
  if (registrationStream) {
    registrationStream.close()
    registrationStream = null
  }
}

const reviewPendingRegistration = (item) => {
  selectedPendingAdapterName.value = item?.adapterName || ''
  registerPreview.value = cloneJson(item?.parsedConfig || null)
}

const completeReviewedRegistration = () => {
  if (!selectedPendingAdapterName.value) return
  const item = pendingRegistrations.value.find(row => row.adapterName === selectedPendingAdapterName.value)
  if (item) completePendingRegistration(item)
}

const openRegisterDrawer = () => {
  registerPreview.value = null
  selectedPendingAdapterName.value = ''
  connectRegistrationStream()
  registerForm.adapterName = ''
  registerForm.rawConfigFormat = 'JSON'
  registerForm.rawConfigContent = ''
  registerDrawerVisible.value = true
  fetchPendingRegistrations()
}

const fetchPendingRegistrations = async () => {
  pendingLoading.value = true
  try {
    const res = await axios.get('/api/adapter/protocol/pending-registrations')
    pendingRegistrations.value = res.data?.success ? asArray(res.data.data) : []
  } finally {
    pendingLoading.value = false
  }
}

const completePendingRegistration = async (item) => {
  if (!item?.adapterName) return
  registerLoading.value = true
  try {
    const reviewedConfig = selectedPendingAdapterName.value === item.adapterName ? registerPreview.value : item.parsedConfig
    const res = await axios.post(`/api/adapter/protocol/pending-registrations/${encodeURIComponent(item.adapterName)}/complete`, { parsedConfig: reviewedConfig || item.parsedConfig })
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '注册失败')
      return
    }
    ElMessage.success('Adapter \u5df2\u6ce8\u518c')
    registerDrawerVisible.value = false
    selectedPendingAdapterName.value = ''
    registerPreview.value = null
    await fetchData()
    activeKey.value = adapterIdOf(res.data.data)
  } finally {
    registerLoading.value = false
  }
}

const discardPendingRegistration = async (item) => {
  if (!item?.adapterName) return
  await axios.delete(`/api/adapter/protocol/pending-registrations/${encodeURIComponent(item.adapterName)}`)
  pendingRegistrations.value = pendingRegistrations.value.filter(row => row.adapterName !== item.adapterName)
}
const parseRegisterConfig = async () => {
  if (!registerForm.rawConfigContent.trim()) {
    ElMessage.warning('\u8bf7\u5148\u586b\u5199\u6216\u4e0a\u4f20\u914d\u7f6e\u5185\u5bb9')
    return
  }
  registerLoading.value = true
  try {
    const res = await axios.post('/api/adapter/index/parse-register', buildRegisterPayload())
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '解析失败')
      return
    }
    registerPreview.value = res.data.data
    if (!registerForm.adapterName && registerPreview.value?.adapterName) registerForm.adapterName = registerPreview.value.adapterName
    ElMessage.success('解析成功')
  } finally {
    registerLoading.value = false
  }
}

const registerAdapter = async () => {
  if (!registerForm.rawConfigContent.trim()) {
    ElMessage.warning('\u8bf7\u5148\u586b\u5199\u6216\u4e0a\u4f20\u914d\u7f6e\u5185\u5bb9')
    return
  }
  registerLoading.value = true
  try {
    const res = await axios.post('/api/adapter/index/register', buildRegisterPayload())
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '注册失败')
      return
    }
    registerDrawerVisible.value = false
    ElMessage.success('Adapter \u5df2\u6ce8\u518c')
    await fetchData()
    activeKey.value = adapterIdOf(res.data.data)
  } finally {
    registerLoading.value = false
  }
}

const deleteAdapter = async (adapter) => {
  await ElMessageBox.confirm(`确认删除 Adapter「${adapter.adapterName}」？`, '删除确认', { type: 'warning' })
  const res = await axios.delete(`/api/adapter/index/delete/${adapter.id}`)
  if (!res.data?.success) {
    ElMessage.error(res.data?.message || '删除失败')
    return
  }
  ElMessage.success('\u5df2\u5220\u9664')
  await fetchData()
}

const importRegisterFile = (file) => {
  const rawFile = file?.raw
  if (!rawFile) return false
  const reader = new FileReader()
  reader.onload = () => { registerForm.rawConfigContent = String(reader.result || '') }
  reader.readAsText(rawFile, 'utf-8')
  return false
}

const buildRegisterPayload = () => {
  const payload = {
    adapterName: registerForm.adapterName || manifestAdapterName(registerForm.rawConfigContent),
    rawConfigFormat: registerForm.rawConfigFormat,
    rawConfigContent: registerForm.rawConfigContent,
    timestamp: Date.now()
  }
  if (registerPreview.value) payload.parsedConfig = registerPreview.value
  return payload
}

const manifestAdapterName = (content) => {
  try { return JSON.parse(content)?.adapterName || '' } catch { return '' }
}

const cloneJson = value => value == null ? null : JSON.parse(JSON.stringify(value))
const asArray = value => Array.isArray(value) ? value : []
const adapterIdOf = adapter => String(adapter?.id || adapter?.adapterName || '')
const parsedConfigOf = adapter => {
  if (!adapter?.parsedConfig) return {}
  if (typeof adapter.parsedConfig === 'string') {
    try { return JSON.parse(adapter.parsedConfig) } catch { return {} }
  }
  return adapter.parsedConfig || {}
}
const adapterCategoriesOf = config => {
  const categories = asArray(config?.deviceCategories)
  if (categories.length) return categories
  const templates = asArray(config?.deviceTemplates)
  const points = asArray(config?.devicePoints)
  return templates.map(template => {
    const templateName = template.templateName || template.name || ''
    return {
      categoryName: template.categoryName || templateName,
      categoryDescription: template.categoryDescription || template.description || '',
      deviceTemplate: template,
      devicePoints: points.filter(point => String(point.templateName || '') === String(templateName))
    }
  })
}
const adapterPointsOf = config => {
  const categories = adapterCategoriesOf(config)
  if (categories.length) return categories.flatMap(category => asArray(category.devicePoints).map(point => ({ ...point, categoryName: category.categoryName })))
  return asArray(config?.devicePoints)
}
const categoryCount = adapter => adapterCategoriesOf(parsedConfigOf(adapter)).length
const pendingCategoryCount = row => row.categoryCount || adapterCategoriesOf(row.parsedConfig).length
const templateCount = adapter => {
  const config = parsedConfigOf(adapter)
  const categories = adapterCategoriesOf(config)
  return categories.length ? categories.length : asArray(config.deviceTemplates).length
}
const pointCount = adapter => adapterPointsOf(parsedConfigOf(adapter)).length
const modelNameOf = id => models.value[String(id)] || String(id || '-')
const boundAdapterOf = instance => instance?.boundAdapterName || instance?.instanceConfig?.boundAdapterName || instance?.instanceConfig?.adapterName || ''
const boundDevicePointOf = instance => instance?.boundDevicePoint || instance?.instanceConfig?.boundDevicePoint || instance?.instanceConfig?.devicePoint || ''
const statusLabel = status => status || 'UNKNOWN'
const statusClass = status => String(status || 'unknown').toLowerCase()
const formatTime = value => value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
const eventList = events => [
  ...asArray(events?.cmdEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '指令周期' })),
  ...asArray(events?.opEvents).map(event => ({ name: event.name || event.eventName, description: event.description || '', type: '业务事件' }))
]
const eventCount = events => eventList(events).length
const visibleCommandParams = command => asArray(command?.parameters || command?.commandParameters).filter(param => !param.hidden)

onMounted(() => {
  fetchData()
  connectRegistrationStream()
})
onUnmounted(closeRegistrationStream)
</script>

<style scoped>
.adapter-management-page { height: calc(100vh - 52px); min-height: 0; display: flex; flex-direction: column; background: #eef2f6; color: #172033; overflow: hidden; }
.page-toolbar { height: 60px; padding: 0 16px; border-bottom: 1px solid #cbd5e1; background: #fff; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.toolbar-title h1 { margin: 0; font-size: 20px; line-height: 1.2; color: #0f172a; }
.toolbar-title span { color: #64748b; font-size: 12px; }
.toolbar-actions { display: flex; align-items: center; gap: 8px; }
.toolbar-actions .el-input { width: 220px; }
.adapter-workspace { flex: 1; min-height: 0; display: grid; grid-template-columns: 330px minmax(0, 1fr); }
.adapter-sidebar { min-width: 0; background: #f8fafc; border-right: 1px solid #cbd5e1; display: flex; flex-direction: column; overflow: hidden; }
.list-title { height: 42px; padding: 0 12px; border-top: 1px solid #e2e8f0; border-bottom: 1px solid #e2e8f0; background: #fff; display: flex; align-items: center; justify-content: space-between; }
.list-title strong { font-size: 14px; color: #0f172a; }
.list-title em { font-style: normal; color: #64748b; font-size: 12px; }
.adapter-list { flex: 1; min-height: 0; overflow: auto; padding: 10px; }
.adapter-list-item { width: 100%; margin-bottom: 8px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; text-align: left; cursor: pointer; display: grid; gap: 5px; }
.adapter-list-item.active { border-color: #2563eb; background: #eff6ff; }
.adapter-name { color: #0f172a; font-size: 14px; font-weight: 800; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-subline { color: #64748b; font-size: 12px; }
.adapter-status { width: fit-content; padding: 2px 7px; border-radius: 999px; background: #e2e8f0; color: #475569; font-size: 11px; font-weight: 800; }
.adapter-status.online, .adapter-status.alive, .adapter-status.registered { background: #dcfce7; color: #15803d; }
.adapter-status.degraded { background: #fef3c7; color: #b45309; }
.adapter-detail { min-width: 0; min-height: 0; overflow: auto; padding: 12px; }
.empty-detail { background: #fff; display: flex; align-items: center; justify-content: center; }
.detail-header { min-height: 58px; padding: 10px 12px; border: 1px solid #cbd5e1; border-radius: 7px; background: #fff; display: flex; align-items: center; justify-content: space-between; }
.detail-header h2 { margin: 2px 0 0; font-size: 22px; line-height: 1.2; color: #0f172a; }
.detail-actions { display: flex; gap: 8px; }
.metric-grid { margin-top: 10px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 8px; }
.metric-grid article { padding: 10px 12px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; display: grid; gap: 5px; }
.metric-grid strong { font-size: 16px; color: #0f172a; }
.adapter-tabs { margin-top: 10px; padding: 0 10px 10px; border: 1px solid #cbd5e1; border-radius: 7px; background: #fff; }
.content-block { margin-top: 10px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; }
.block-head { margin-bottom: 8px; display: flex; align-items: center; justify-content: space-between; }
.block-head h3 { margin: 0; font-size: 16px; color: #0f172a; }
.block-head em { font-style: normal; color: #64748b; font-size: 12px; }
.template-tags, .param-tags, .mapping-chips { display: flex; align-items: center; flex-wrap: wrap; gap: 6px; }
.mapping-chips span { padding: 2px 6px; border: 1px solid #bfdbfe; border-radius: 4px; color: #1d4ed8; background: #eff6ff; font-size: 12px; }
.mapping-chips em { color: #94a3b8; font-style: normal; }
.industrial-table :deep(.el-table__cell) { padding: 7px 8px; }
.json-block pre { max-height: 500px; overflow: auto; margin: 0; padding: 10px; border: 1px solid #e2e8f0; border-radius: 5px; background: #0f172a; color: #e2e8f0; font-size: 12px; line-height: 1.5; }
.register-form :deep(.el-select) { width: 100%; }
.upload-row { display: flex; gap: 8px; align-items: center; }
.register-flow-card { margin-bottom: 10px; padding: 12px; border: 1px solid #bfdbfe; border-left: 3px solid #2563eb; border-radius: 6px; background: #eff6ff; }
.flow-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.flow-head div { display: grid; gap: 4px; min-width: 0; }
.flow-head span { color: #64748b; font-size: 12px; }
.flow-head strong { color: #0f172a; font-size: 15px; word-break: break-all; }
.flow-actions { margin-top: 10px; display: flex; gap: 8px; flex-wrap: wrap; }
.pending-register-panel { min-height: 130px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; }
.drawer-section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px; }
.drawer-section-head h3 { margin: 0; color: #0f172a; font-size: 15px; }
.drawer-section-head span { color: #64748b; font-size: 12px; }
.register-review-panel { margin-top: 10px; padding: 10px; border: 1px solid #cbd5e1; background: #fff; }
.review-form { margin-bottom: 8px; }
.review-actions { height: 42px; display: flex; align-items: flex-end; justify-content: flex-end; }
.review-table :deep(.el-input__wrapper) { box-shadow: none; border-radius: 0; }
.manual-register-panel { margin-top: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #fff; padding: 10px; }
.manual-register-panel summary { cursor: pointer; color: #0f172a; font-weight: 700; }
.manual-register-panel .register-form { margin-top: 10px; }
.manual-actions { display: flex; justify-content: flex-end; gap: 8px; }
.preview-box { margin-top: 8px; padding: 10px; border: 1px solid #bfdbfe; border-radius: 6px; background: #eff6ff; display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
.preview-box div { display: grid; gap: 4px; }
.preview-box span { color: #64748b; font-size: 12px; }
.preview-box strong { color: #0f172a; font-size: 15px; }
.drawer-footer { display: flex; justify-content: flex-end; gap: 8px; }
.mqtt-strip { height: 46px; padding: 0 12px; border-bottom: 1px solid #cbd5e1; background: #fff; display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 8px; }
.strip-label { color: #64748b; font-size: 12px; font-weight: 700; }
.mqtt-strip strong { color: #0f172a; font-size: 14px; }
.adapter-list { padding: 0; }
.adapter-list-item { margin: 0; border: 0; border-bottom: 1px solid #dbe4ef; border-radius: 0; background: #fff; grid-template-columns: 1fr auto; align-items: center; column-gap: 8px; }
.adapter-list-item.active { border-color: #dbe4ef; background: #eaf2ff; box-shadow: inset 3px 0 0 #2563eb; }
.adapter-subline { grid-column: 1 / 3; }
.adapter-status { border-radius: 3px; }
.adapter-detail { padding: 0; background: #eef2f6; }
.detail-header { min-height: 58px; padding: 10px 14px; border-width: 0 0 1px; border-radius: 0; }
.runtime-table { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); border-bottom: 1px solid #cbd5e1; background: #fff; }
.runtime-table div { min-width: 0; padding: 9px 12px; border-right: 1px solid #e2e8f0; display: grid; gap: 3px; }
.runtime-table div:last-child { border-right: 0; }
.runtime-table span { color: #64748b; font-size: 12px; }
.runtime-table strong { color: #0f172a; font-size: 14px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.adapter-tabs { margin: 0; padding: 0 12px 12px; border-width: 0; border-radius: 0; background: #fff; }
.adapter-tabs :deep(.el-tabs__header) { margin: 0; }
.content-block { margin-top: 10px; padding: 0; border: 1px solid #cbd5e1; border-radius: 0; background: #fff; }
.block-head { height: 40px; margin: 0; padding: 0 10px; border-bottom: 1px solid #dbe4ef; background: #f8fafc; }
.relation-name { display: flex; align-items: center; gap: 8px; min-width: 0; }
.relation-name strong { color: #0f172a; }
.relation-name span { color: #64748b; font-size: 12px; }
.mapping-chips span { border-radius: 2px; background: #f8fbff; }
.mapping-chips b { margin-right: 4px; color: #0f172a; }
.mapping-chips i { margin: 0 4px; color: #64748b; font-style: normal; }
.param-table-list { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; }
.param-table-list span { display: inline-flex; align-items: center; overflow: hidden; border: 1px solid #cbd5e1; background: #fff; }
.param-table-list b { padding: 2px 7px; color: #0f172a; font-weight: 700; }
.param-table-list em { padding: 2px 7px; border-left: 1px solid #cbd5e1; background: #f1f5f9; color: #475569; font-style: normal; }
.param-table-list i { color: #94a3b8; font-style: normal; }
.binding-tree { padding: 8px 10px; }
.binding-tree :deep(.el-tree-node__content) { height: 34px; border-bottom: 1px solid #eef2f6; }
.binding-node { width: 100%; display: grid; grid-template-columns: minmax(180px, 0.6fr) minmax(220px, 1fr); gap: 14px; align-items: center; }
.binding-node.model .binding-label { font-weight: 800; color: #0f172a; }
.binding-node.instance .binding-label { color: #1d4ed8; font-weight: 700; }
.binding-node.point .binding-label { color: #059669; }
.binding-meta { color: #64748b; font-size: 12px; }
.register-flow-card, .pending-register-panel, .register-review-panel, .manual-register-panel, .preview-box { border-radius: 0; }
.preview-box { background: #fff; border-color: #cbd5e1; }
@media (max-width: 1120px) { .adapter-workspace { grid-template-columns: 280px minmax(0, 1fr); } .template-grid, .command-grid, .metric-grid { grid-template-columns: 1fr; } }
</style>

````

---

## Frontend/src/views/device/DeviceInstanceManagement.vue

````text

<template>
  <div class="instance-page">
    <el-container class="layout">
      <el-aside width="320px" class="sidebar">
        <div class="sidebar-header">
          <div class="header-title">
            <span>设备模型分类</span>
          </div>
        </div>
        <el-scrollbar class="sidebar-scroll" v-loading="modelsLoading">
          <div class="model-list" v-for="group in groupedModels" :key="group.category">
            <div class="category-title" style="padding: 10px 16px; font-weight: bold; color: #909399; font-size: 12px; background: #f8f9fa;">
              {{ group.category }}
            </div>
            <div
              v-for="model in group.items"
              :key="model.modelId"
              :class="['model-item', { active: selectedModelId === model.modelId }]"
              @click="selectModel(model.modelId)"
            >
              <div class="model-name">{{ model.modelName }}</div>
              <div class="model-meta">编号: {{ model.modelId }}</div>
            </div>
          </div>
        </el-scrollbar>
      </el-aside>

      <el-main class="content">
        <div class="main-header">
          <div>
            <h2>{{ selectedModelName }}</h2>
            <span class="subtitle">设备实例查看与配置</span>
          </div>
          <div class="actions">
            <el-input
              v-model="instanceKeyword"
              class="instance-search"
              size="small"
              clearable
              placeholder="搜索实例或设备SN"
              @input="onInstanceSearchInput"
            />
            <el-button :icon="Refresh" circle size="small" @click="loadData" title="刷新" />
            <el-button v-if="canCreateInstance" type="primary" class="add-device-trigger" @click="openCreateDialog">
              <el-icon><Plus /></el-icon> 添加设备
            </el-button>
          </div>
        </div>



        <div class="instance-list-wrap" v-loading="loading">
          <el-table
            v-if="instances.length > 0"
            :data="instances"
            border
            stripe
            size="small"
            class="instance-table"
            row-key="instanceId"
            @row-click="viewDetails"
          >
            <el-table-column label="设备实例" min-width="180">
              <template #default="{ row }">
                <div class="instance-name-cell">
                  <strong>{{ row.instanceName }}</strong>
                  <span>实例ID {{ row.instanceId }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="设备模型" min-width="160">
              <template #default="{ row }">{{ getModelName(row.modelId) }}</template>
            </el-table-column>
            <el-table-column label="Adapter / 设备点" min-width="220">
              <template #default="{ row }">
                <div class="binding-cell">
                  <span><b>Adapter</b>{{ row.boundAdapterName || row.commConfig?.boundAdapterName || '\u672a\u5206\u914d' }}</span>
                  <span><b>\u8bbe\u5907\u70b9</b>{{ row.boundDevicePoint || row.commConfig?.boundDevicePoint || '\u672a\u5206\u914d' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="MQTT 主题" min-width="360">
              <template #default="{ row }">
                <div class="topic-cell">
                  <span v-for="topic in mqttTopicRows(row.boundAdapterName, row.boundDevicePoint)" :key="topic.type">
                    <b>{{ topic.label }}</b><code>{{ topic.topic || '-' }}</code>
                  </span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="在线状态" width="105" align="center">
              <template #default="{ row }">
                <el-tag :type="row.isOnline ? 'success' : 'info'" size="small" effect="plain">{{ row.isOnline ? '在线' : '离线' }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
          <div v-else class="empty-wrap">
            <el-empty description="暂无设备实例" :image-size="100" />
          </div>
        </div>
        <div class="instance-pagination">
          <el-pagination
            v-model:current-page="instancePageNo"
            v-model:page-size="instancePageSize"
            :page-sizes="[12, 24, 48, 96]"
            :total="instanceTotal"
            layout="total, sizes, prev, pager, next"
            background
            small
            @size-change="handleInstancePageSizeChange"
            @current-change="loadInstances"
          />
        </div>
      </el-main>
    </el-container>

    <el-dialog
      v-model="drawerVisible"
      :title="`设备实例: ${activeInstance?.instanceName || ''}`"
      width="980px"
      :destroy-on-close="true"
      @close="closeDrawer"
    >
      <div v-if="activeInstance" class="drawer-body">
        <el-tabs v-model="activeTab">
          <el-tab-pane label="基础配置" name="info">
            <el-form label-position="top" size="small" class="instance-info-grid">
              <el-form-item label="实例编号">
                <el-input v-model="activeInstance.instanceId" disabled />
              </el-form-item>
              <el-form-item label="实例名称">
                <el-input v-model="activeInstance.instanceName" />
              </el-form-item>
              <el-form-item label="设备模型">
                <el-select v-model="activeInstance.modelId" style="width: 100%" @change="onModelChangeInDrawer">
                  <el-option v-for="m in models" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                </el-select>
              </el-form-item>
              <el-form-item label="在线状态">
                <div class="readonly-status">
                  <el-tag :type="activeInstance.isOnline ? 'success' : 'info'" effect="plain">{{ activeInstance.isOnline ? '在线' : '离线' }}</el-tag>
                  <span>由 Adapter 心跳 / 运行状态返回</span>
                </div>
              </el-form-item>
              <el-form-item label="Adapter">
                <el-select v-model="activeInstance.boundAdapterName" style="width: 100%" filterable @change="onAdapterChangeInDrawer">
                  <el-option v-for="adapter in adapterOptions" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
                </el-select>
              </el-form-item>
              <el-form-item label="设备点">
                <el-select v-model="activeInstance.boundDevicePoint" style="width: 100%" filterable :disabled="!activeInstance.boundAdapterName">
                  <el-option v-for="point in drawerDevicePointOptions" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
                </el-select>
              </el-form-item>
            </el-form>

            <section class="topic-section">
              <div class="section-caption">MQTT 主题</div>
              <el-table :data="activeMqttTopicRows" border size="small" class="topic-table">
                <el-table-column label="用途" width="110" prop="label" />
                <el-table-column label="方向" width="135" prop="direction" />
                <el-table-column label="主题">
                  <template #default="{ row }"><code>{{ row.topic || '-' }}</code></template>
                </el-table-column>
              </el-table>
            </section>

            <div class="footer-actions">
              <el-popconfirm v-if="canDeleteInstance" title="确认删除该设备实例？" @confirm="deleteInstance(activeInstance.instanceId)">
                <template #reference>
                  <el-button type="danger" plain size="small">删除设备</el-button>
                </template>
              </el-popconfirm>
              <el-button v-if="canEditInstance" type="primary" size="small" :loading="saving" @click="saveInstance">保存</el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane v-if="canControlInstance" label="手动控制" name="control">
            <el-form label-position="top" size="small" class="manual-control-form">
              <el-form-item label="设备功能">
                <el-select v-model="controlCommandId" style="width: 100%" placeholder="选择模型定义的功能" @change="resetControlParams">
                  <el-option
                    v-for="cmd in activeInstanceCommands"
                    :key="cmd.commandId"
                    :label="`${cmd.commandName || cmd.commandId} / Adapter: ${cmd.adapterCommandName || '-'}`"
                    :value="cmd.commandId"
                  />
                </el-select>
              </el-form-item>
              <div class="control-param-grid" v-if="activeControlParams.length">
                <el-form-item v-for="param in activeControlParams" :key="paramKey(param)" :label="param.displayName || param.name || param.paramName">
                  <el-switch v-if="isBooleanType(param.dataType)" v-model="controlParamValues[paramKey(param)]" />
                  <el-input-number
                    v-else-if="isNumberType(param.dataType)"
                    v-model="controlParamValues[paramKey(param)]"
                    :precision="isIntegerType(param.dataType) ? 0 : undefined"
                    controls-position="right"
                    style="width: 100%"
                  />
                  <el-input v-else v-model="controlParamValues[paramKey(param)]" :placeholder="param.dataType || 'STRING'" />
                  <div class="field-hint">数据类型：{{ param.dataType || '-' }}</div>
                </el-form-item>
              </div>
              <div v-else class="empty-inline">该功能无外部参数</div>
            </el-form>
            <div class="footer-actions">
              <el-button type="primary" size="small" :loading="sendingControl" @click="sendManualCommand">
                发送指令
              </el-button>
            </div>
          </el-tab-pane>

          <el-tab-pane label="实时状态" name="status">
            <div class="status-top">
              <el-tag type="success" size="small">状态扫描中（每 3 秒刷新）</el-tag>
              <span>更新时间: {{ lastSnapshotTime }}</span>
            </div>
            <el-descriptions :column="1" border size="small" class="mb-12">
              <el-descriptions-item label="指令状态">{{ snapshot?.currentCommandState || '-' }}</el-descriptions-item>
              <el-descriptions-item label="功能状态">{{ snapshot?.currentOperationState || '-' }}</el-descriptions-item>
            </el-descriptions>
            <section class="snapshot-section">
              <div class="section-caption">属性快照</div>
              <el-table :data="snapshotAttributeRows" border size="small" class="snapshot-table">
                <el-table-column label="模型属性" min-width="160" prop="label" />
                <el-table-column label="属性标识" min-width="150" prop="key" />
                <el-table-column label="数据类型" width="110" prop="dataType" />
                <el-table-column label="单位" width="90" prop="unit" />
                <el-table-column label="当前值" min-width="160">
                  <template #default="{ row }"><span class="mono">{{ row.value ?? '-' }}</span></template>
                </el-table-column>
              </el-table>
            </section>
          </el-tab-pane>

          <el-tab-pane label="局部约束" name="constraints">
            <div class="constraint-actions">
              <el-button v-if="canEditInstance" type="primary" plain size="small" @click="addConstraint">
                <el-icon><Plus /></el-icon> 新增约束
              </el-button>
            </div>
            <el-table :data="localConstraints" border size="small">
              <el-table-column label="监控属性">
                <template #default="{ row }">
                  <el-select v-model="row.targetAttr" size="small" style="width: 100%" placeholder="选择属性">
                    <el-option
                      v-for="prop in getModelAttributes(activeInstance.modelId)"
                      :key="prop.identifier"
                      :label="prop.name"
                      :value="prop.identifier"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="条件" width="100">
                <template #default="{ row }">
                  <el-select v-model="row.operator" size="small">
                    <el-option label=">" value=">" />
                    <el-option label=">=" value=">=" />
                    <el-option label="<" value="<" />
                    <el-option label="<=" value="<=" />
                    <el-option label="=" value="==" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="阈值" width="120">
                <template #default="{ row }">
                  <el-input v-model="row.threshold" size="small" />
                </template>
              </el-table-column>
              <el-table-column v-if="canEditInstance" label="操作" width="70" align="center">
                <template #default="{ $index }">
                  <el-button type="danger" link size="small" @click="removeConstraint($index)">删除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <div class="footer-actions mt-12">
              <el-button v-if="canEditInstance" type="primary" size="small" :loading="saving" @click="saveConstraints">保存约束</el-button>
            </div>
          </el-tab-pane>


          <el-tab-pane label="结构拓扑" name="components">
            <section class="component-create-panel" v-if="canEditInstance">
              <el-input v-model="componentForm.componentName" size="small" placeholder="新增槽位名称" />
              <el-select v-model="componentForm.categoryId" size="small" clearable filterable placeholder="组件类别">
                <el-option v-for="cat in categories" :key="String(cat.id)" :label="cat.categoryName" :value="String(cat.id)" />
              </el-select>
              <el-button type="primary" size="small" :loading="savingComponent" @click="saveComponent">新增槽位</el-button>
            </section>
            <el-table :data="instanceComponents" border size="small" v-loading="loadingComponents" class="component-table">
              <el-table-column label="组件槽位" min-width="150" prop="componentName" />
              <el-table-column label="类别" min-width="130">
                <template #default="{ row }">{{ categoryNameById(row.categoryId) || '-' }}</template>
              </el-table-column>
              <el-table-column label="绑定实例" min-width="150">
                <template #default="{ row }">{{ instanceNameById(row.selfInstanceId) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag size="small" :type="componentStatusType(row.status)" effect="plain">{{ row.status || '未配置' }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="安装时间" min-width="150">
                <template #default="{ row }">{{ formatTime(row.installTime) }}</template>
              </el-table-column>
              <el-table-column label="规格" min-width="180">
                <template #default="{ row }"><code>{{ componentSpecBrief(row.specification) }}</code></template>
              </el-table-column>
              <el-table-column v-if="canEditInstance" label="操作" width="230" fixed="right">
                <template #default="{ row }">
                  <el-button v-if="!row.selfInstanceId && row.status !== '使用中'" link type="primary" size="small" @click="openComponentAction(row, 'configure')">配置</el-button>
                  <el-button v-else link type="primary" size="small" @click="openComponentAction(row, 'replace')">更换</el-button>
                  <el-button link size="small" @click="showComponentHistory(row)">历史</el-button>
                  <el-button link type="warning" size="small" @click="discardComponent(row)">废弃</el-button>
                  <el-popconfirm title="确认删除该组件槽位？" @confirm="deleteComponent(row)">
                    <template #reference><el-button link type="danger" size="small">删除</el-button></template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="数据集" name="datasets">
            <div v-if="canCreateDataset" class="dataset-create-panel">
              <el-select v-model="datasetCreateForm.templateId" size="small" filterable clearable placeholder="选择数据模板">
                <el-option v-for="tpl in availableDataTemplates" :key="templateIdOf(tpl)" :label="tpl.templateName || '未命名模板'" :value="templateIdOf(tpl)" />
              </el-select>
              <el-input v-model="datasetCreateForm.dataDesc" size="small" placeholder="数据集名称，例如：高压报警专项数据" />
              <el-button type="primary" size="small" :loading="creatingDataSet" @click="createDataSetForInstance">绑定模板建表</el-button>
            </div>
            <el-table :data="instanceDataSets" border size="small" v-loading="loadingDataSets">
              <el-table-column prop="id" label="数据集ID" width="90" />
              <el-table-column prop="dataDesc" label="数据集描述" min-width="160" />
              <el-table-column prop="dataTable" label="底层物理表" min-width="180" />
              <el-table-column prop="createTime" label="创建时间" min-width="160">
                <template #default="{ row }">{{ row.createTime ? new Date(row.createTime).toLocaleString() : '-' }}</template>
              </el-table-column>
              <el-table-column v-if="canDeleteDataset" label="操作" width="90" align="center">
                <template #default="{ row }">
                  <el-popconfirm title="确认删除该数据表？物理表会同时删除。" @confirm="deleteInstanceDataSet(row)">
                    <template #reference>
                      <el-button link type="danger" size="small">删除</el-button>
                    </template>
                  </el-popconfirm>
                </template>
              </el-table-column>
            </el-table>
            <div class="footer-actions mt-12">
              <el-button type="primary" size="small" @click="loadDataSets">刷新列表</el-button>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>


    <el-dialog v-model="componentActionVisible" :title="componentActionMode === 'replace' ? '更换组件' : '配置组件'" width="560px" append-to-body>
      <el-form label-position="top" size="small" class="component-action-form">
        <el-form-item label="组件槽位">
          <el-input v-model="componentActionForm.componentName" />
        </el-form-item>
        <el-form-item label="绑定设备实例">
          <el-select v-model="componentActionForm.selfInstanceId" clearable filterable style="width: 100%" placeholder="不绑定数字化实例时可只填写规格">
            <el-option v-for="item in instances" :key="item.instanceId" :label="item.instanceName" :value="item.instanceId" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格信息 JSON">
          <el-input v-model="componentActionForm.specificationText" type="textarea" :rows="4" placeholder="例如：{&quot;brand&quot;:&quot;A&quot;,&quot;model&quot;:&quot;M1&quot;}" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="componentActionVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="savingComponent" @click="submitComponentAction">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="componentHistoryVisible" title="组件历史" width="720px" append-to-body>
      <el-table :data="componentHistoryRows" border size="small" v-loading="loadingComponentHistory">
        <el-table-column label="组件槽位" min-width="150" prop="componentName" />
        <el-table-column label="绑定实例" min-width="150">
          <template #default="{ row }">{{ instanceNameById(row.selfInstanceId) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" prop="status" />
        <el-table-column label="安装时间" min-width="150">
          <template #default="{ row }">{{ formatTime(row.installTime) }}</template>
        </el-table-column>
        <el-table-column label="前序ID" width="90" prop="predecessorId" />
      </el-table>
    </el-dialog>
    <el-dialog v-model="createDialogVisible" title="添加设备实例" width="760px" class="instance-create-dialog" append-to-body :destroy-on-close="true">
      <div class="instance-create-body">
        <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="92px" label-position="top" size="small" class="instance-create-form">
          <el-form-item label="设备名称" prop="instanceName">
            <el-input v-model="createForm.instanceName" />
          </el-form-item>
          <el-form-item label="设备模型" prop="modelId">
            <el-select
              v-model="createForm.modelId"
              style="width: 100%"
              placeholder="搜索并选择设备模型"
              filterable
              remote
              reserve-keyword
              :remote-method="searchModels"
              :loading="modelSearchLoading"
              @change="onModelChangeInCreate"
            >
              <el-option v-for="m in modelOptions" :key="m.modelId" :label="`${m.modelName}（${m.modelId}）`" :value="m.modelId" />
            </el-select>
          </el-form-item>

          <el-form-item label="Adapter" prop="boundAdapterName">
            <el-select v-model="createForm.boundAdapterName" style="width: 100%" filterable :loading="adapterLoading" @change="onAdapterChangeInCreate">
              <el-option v-for="adapter in adapterOptions" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
            </el-select>
          </el-form-item>
          <el-form-item label="设备点" prop="boundDevicePoint">
            <el-select v-model="createForm.boundDevicePoint" style="width: 100%" filterable :loading="pointsLoading" :disabled="!createForm.boundAdapterName" @change="updateCreateTopicPreview">
              <el-option v-for="point in createDevicePointOptions" :key="point.devicePoint" :label="devicePointLabel(point)" :value="point.devicePoint" />
            </el-select>
          </el-form-item>
          <el-form-item label="资产编号">
            <el-input v-model="createForm.assetInfo.serialNumber" placeholder="可选" />
          </el-form-item>
          <el-form-item label="安装位置">
            <el-input v-model="createForm.assetInfo.location" placeholder="例如：A区实验台 1" />
          </el-form-item>
          <el-form-item label="采购日期">
            <el-date-picker v-model="createForm.assetInfo.purchaseDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="安装日期">
            <el-date-picker v-model="createForm.assetInfo.installDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </el-form-item>
          <el-form-item label="备注" class="form-wide">
            <el-input v-model="createForm.assetInfo.notes" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="MQTT 主题预览" class="form-wide">
            <div class="topic-preview-grid">
              <div v-for="topic in createMqttTopicRows" :key="topic.type">
                <span>{{ topic.label }}</span>
                <code>{{ topic.topic || '-' }}</code>
              </div>
            </div>
          </el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button size="small" @click="createDialogVisible = false">取消</el-button>
        <el-button size="small" type="primary" :loading="creating" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { Cpu, Plus, Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import axios from 'axios'
import { useAuthStore } from '../../stores/authStore'

interface DeviceModel {
  modelId: string
  modelName: string
  deviceCategory: string
  capabilitySpec: any
}

interface AdapterOption {
  adapterName: string
  parsedConfig?: any
}

interface AdapterDevicePoint {
  devicePoint: string
  templateName: string
  description?: string
}

interface DeviceInstance {
  instanceId: string
  modelId: string
  stateMachineId: string
  instanceName: string
  boundAdapterName?: string
  boundDevicePoint?: string
  commConfig: {
    boundAdapterName?: string
    boundDevicePoint?: string
    mqttTopic?: string
    mqttTopics?: Array<{ type: string; label: string; direction: string; topic: string }>
    constraints?: Array<{ targetAttr: string; operator: string; threshold: string }>
  }
  isOnline: boolean
}

interface DeviceSnapshot {
  instanceId: string
  currentCommandState?: string
  currentOperationState?: string
  latestAttributes?: Record<string, any>
}

const authStore = useAuthStore()

const models = ref<DeviceModel[]>([])
const modelOptions = ref<DeviceModel[]>([])
const instances = ref<DeviceInstance[]>([])
const adapterOptions = ref<AdapterOption[]>([])
const createDevicePointOptions = ref<AdapterDevicePoint[]>([])
const drawerDevicePointOptions = ref<AdapterDevicePoint[]>([])
const categories = ref<any[]>([])
const categoriesMap = ref<Record<string, string>>({})
const selectedModelId = ref('')
const instanceKeyword = ref('')
const loading = ref(false)
const modelsLoading = ref(false)
const modelSearchLoading = ref(false)
const saving = ref(false)
const creating = ref(false)
const sendingControl = ref(false)
const adapterLoading = ref(false)
const pointsLoading = ref(false)

const drawerVisible = ref(false)
const activeInstance = ref<DeviceInstance | null>(null)
const activeTab = ref('info')

const snapshot = ref<DeviceSnapshot | null>(null)
const lastSnapshotTime = ref('-')
let pollingTimer: any = null
const instancePageNo = ref(1)
const instancePageSize = ref(24)
const instanceTotal = ref(0)
let modelSearchTimer: any = null
let instanceLoadSeq = 0
let instanceSearchTimer: any = null

const localConstraints = ref<Array<{ targetAttr: string; operator: string; threshold: string }>>([])
const controlCommandId = ref('')
const controlParamValues = ref<Record<string, any>>({})

const instanceDataSets = ref<any[]>([])
const loadingDataSets = ref(false)
const dataTemplates = ref<any[]>([])
const creatingDataSet = ref(false)
const datasetCreateForm = ref({ templateId: '', dataDesc: '' })
const instanceComponents = ref<any[]>([])
const loadingComponents = ref(false)
const savingComponent = ref(false)
const componentForm = ref({ componentName: '', categoryId: '', selfInstanceId: '', status: '\u672a\u914d\u7f6e' })
const componentActionVisible = ref(false)
const componentActionMode = ref<'configure' | 'replace'>('configure')
const activeComponent = ref<any>(null)
const componentActionForm = ref({ componentName: '', selfInstanceId: '', specificationText: '{}' })
const componentHistoryVisible = ref(false)
const componentHistoryRows = ref<any[]>([])
const loadingComponentHistory = ref(false)

const createDialogVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({
  instanceName: '',
  modelId: '',
  boundAdapterName: '',
  boundDevicePoint: '',
  mqttTopicPreview: '',
  assetInfo: { serialNumber: '', purchaseDate: '', installDate: '', location: '', notes: '' }
})
const createRules = ref<FormRules>({
  instanceName: [{ required: true, message: '\u8bf7\u8f93\u5165\u8bbe\u5907\u540d\u79f0', trigger: 'blur' }],
  modelId: [{ required: true, message: '\u8bf7\u9009\u62e9\u8bbe\u5907\u6a21\u578b', trigger: 'change' }],
  boundAdapterName: [{ required: true, message: '\u8bf7\u9009\u62e9 Adapter', trigger: 'change' }],
  boundDevicePoint: [{ required: true, message: '\u8bf7\u9009\u62e9\u8bbe\u5907\u70b9', trigger: 'change' }]
})

const canCreateInstance = computed(() => authStore.hasPermission('device_instance:create'))
const canEditInstance = computed(() => authStore.hasPermission('device_instance:edit'))
const canDeleteInstance = computed(() => authStore.hasPermission('device_instance:delete'))
const canControlInstance = computed(() => authStore.hasPermission('device_instance:control'))
const canCreateDataset = computed(() => authStore.hasPermission('data_dataset:create'))
const canDeleteDataset = computed(() => authStore.hasPermission('data_dataset:delete'))

const selectedModelName = computed(() => {
  if (!selectedModelId.value) return '\u5168\u90e8\u8bbe\u5907\u5b9e\u4f8b'
  const model = models.value.find(m => String(m.modelId) === String(selectedModelId.value))
  return model ? `${model.modelName} (${model.modelId})` : '\u672a\u77e5\u6a21\u578b'
})

const groupedModels = computed(() => {
  const groups: Record<string, DeviceModel[]> = {}
  models.value.forEach(model => {
    const catId = (model as any).categoryId
    const catName = (catId && categoriesMap.value[catId]) || '\u672a\u5206\u7c7b'
    if (!groups[catName]) groups[catName] = []
    groups[catName].push(model)
  })
  return Object.entries(groups).map(([category, items]) => ({ category, items }))
})

const snapshotAttributes = computed(() => snapshot.value?.latestAttributes || {})

const selectedCreateModel = computed(() => findModelById(createForm.value.modelId))
const activeInstanceModel = computed(() => activeInstance.value ? findModelById(activeInstance.value.modelId) : null)
const activeInstanceCommands = computed(() => {
  const capabilities = asArray(activeInstanceModel.value?.capabilitySpec?.capabilities)
  return capabilities.map((cap: any) => ({
    ...cap,
    commandId: cap.name || cap.commandId || cap.adapterCommandName,
    commandName: cap.displayName || cap.name || cap.commandId,
    adapterCommandName: cap.adapterCommandName || cap.commandName || cap.name,
    parameters: asArray(cap.parameters)
  })).filter((cap: any) => cap.commandId)
})
const activeControlCommand = computed(() => activeInstanceCommands.value.find((cmd: any) => cmd.commandId === controlCommandId.value) || null)
const activeControlParams = computed(() => asArray(activeControlCommand.value?.parameters).filter((param: any) => !param.hidden))
const createMqttTopicRows = computed(() => mqttTopicRows(createForm.value.boundAdapterName, createForm.value.boundDevicePoint))
const activeMqttTopicRows = computed(() => mqttTopicRows(activeInstance.value?.boundAdapterName, activeInstance.value?.boundDevicePoint))
const snapshotAttributeRows = computed(() => {
  if (!activeInstance.value) return []
  const attrs = asArray(getModelAttributes(activeInstance.value.modelId))
  const snapshotMap = snapshotAttributes.value
  const used = new Set<string>()
  const rows = attrs.map((attr: any) => {
    const candidates = [attr.name, attr.identifier, attr.displayName].filter(Boolean).map(String)
    const matchedKey = candidates.find(key => Object.prototype.hasOwnProperty.call(snapshotMap, key)) || candidates[0] || ''
    if (matchedKey) used.add(matchedKey)
    return {
      key: attr.name || attr.identifier || attr.displayName || '-',
      label: attr.displayName || attr.name || attr.identifier || '-',
      dataType: attr.dataType || '-',
      unit: attr.unit || '-',
      value: matchedKey ? snapshotMap[matchedKey] : undefined
    }
  })
  Object.keys(snapshotMap).forEach(key => {
    if (!used.has(key)) rows.push({ key, label: getAttributeName(key), dataType: '-', unit: '-', value: snapshotMap[key] })
  })
  return rows
})
const availableDataTemplates = computed(() => {
  const modelId = activeInstance.value?.modelId
  return dataTemplates.value.filter(tpl => !tpl.deviceModelId || !modelId || String(tpl.deviceModelId) === String(modelId))
})

const mqttTopicRows = (adapterName?: string, devicePoint?: string) => {
  const ready = !!adapterName && !!devicePoint
  const base = ready ? `smartlab/adapter/${adapterName}/${devicePoint}` : ''
  return [
    { type: 'command', label: '命令', direction: '系统 → Adapter', topic: ready ? `${base}/command` : '' },
    { type: 'telemetry', label: '遥测', direction: 'Adapter → 系统', topic: ready ? `${base}/telemetry` : '' },
    { type: 'event', label: '事件', direction: 'Adapter → 系统', topic: ready ? `${base}/event` : '' },
    { type: 'heartbeat', label: '心跳', direction: 'Adapter → 系统', topic: adapterName ? `smartlab/adapter/${adapterName}/heartbeat` : '' }
  ]
}

const loadData = async () => {
  modelsLoading.value = true
  try {
    const catRes = await axios.get('/api/device/category/list')
    if (catRes.data?.success) {
      const map: Record<string, string> = {}
      categories.value = catRes.data.data || []
      ;(catRes.data.data || []).forEach((c: any) => {
        map[c.id] = c.categoryName
      })
      categoriesMap.value = map
    }
    await Promise.all([loadSidebarModels(), loadModelOptions(), loadAdapters(), loadDataTemplates()])
    await loadInstances()
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备数据失败')
  } finally {
    modelsLoading.value = false
  }
}

const loadSidebarModels = async () => {
  const res = await axios.get('/api/device/model/page', {
    params: {
      pageNo: 1,
      pageSize: 100
    }
  })
  if (res.data?.success) {
    models.value = (res.data.data?.records || []).map(normalizeModel)
    if (models.value.length > 0 && !selectedModelId.value) {
      selectedModelId.value = models.value[0].modelId
    }
  }
}

const loadModelOptions = async (keyword = '') => {
  const res = await axios.get('/api/device/model/page', {
    params: {
      pageNo: 1,
      pageSize: 100,
      keyword: keyword.trim() || undefined
    }
  })
  if (res.data?.success) {
    const records = (res.data.data?.records || []).map(normalizeModel)
    const selected = findModelById(createForm.value.modelId)
    modelOptions.value = selected && !records.some((v: DeviceModel) => v.modelId === selected.modelId)
      ? [selected, ...records]
      : records
  }
}

const loadAdapters = async () => {
  adapterLoading.value = true
  try {
    const res = await axios.get('/api/adapter/index/list')
    if (res.data?.success) {
      adapterOptions.value = res.data.data || []
    }
  } finally {
    adapterLoading.value = false
  }
}

const loadDataTemplates = async () => {
  try {
    const res = await axios.get('/api/data/template/list')
    if (res.data?.success) {
      dataTemplates.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载数据模板失败')
  }
}

const loadDevicePoints = async (adapterName: string, target: 'create' | 'drawer' = 'create') => {
  const listRef = target === 'drawer' ? drawerDevicePointOptions : createDevicePointOptions
  listRef.value = []
  if (!adapterName) return
  pointsLoading.value = true
  try {
    const model = target === 'drawer' ? activeInstanceModel.value : selectedCreateModel.value
    const adapterConfig = model?.capabilitySpec?.adapterContract?.config || {}
    const categoryName = adapterConfig.categoryName || undefined
    const templateName = adapterConfig.templateName || undefined
    const params = categoryName ? { categoryName } : { templateName }
    const res = await axios.get(`/api/adapter/index/${encodeURIComponent(adapterName)}/device-points`, { params })
    if (res.data?.success) {
      listRef.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '\u52a0\u8f7d Adapter \u8bbe\u5907\u70b9\u5931\u8d25')
  } finally {
    pointsLoading.value = false
  }
}
const searchModels = (keyword: string) => {
  if (modelSearchTimer) window.clearTimeout(modelSearchTimer)
  modelSearchTimer = window.setTimeout(async () => {
    modelSearchLoading.value = true
    try {
      await loadModelOptions(keyword)
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '搜索设备模型失败')
    } finally {
      modelSearchLoading.value = false
    }
  }, 250)
}

const loadInstances = async () => {
  const seq = ++instanceLoadSeq
  loading.value = true
  try {
    const instancesRes = await axios.get('/api/device/instance/page', {
      params: {
        pageNo: instancePageNo.value,
        pageSize: instancePageSize.value,
        modelId: selectedModelId.value || undefined,
        keyword: instanceKeyword.value.trim() || undefined
      }
    })
    
    if (seq !== instanceLoadSeq) return
    if (instancesRes.data?.success) {
      const pageData = instancesRes.data.data || {}
      instances.value = (pageData.records || []).map(normalizeInstance)
      instanceTotal.value = pageData.total || 0
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载设备实例失败')
  } finally {
    if (seq === instanceLoadSeq) loading.value = false
  }
}

const selectModel = (id: string) => {
  selectedModelId.value = id
  instancePageNo.value = 1
  loadInstances()
}

const onInstanceSearchInput = () => {
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
  instanceSearchTimer = window.setTimeout(() => {
    instancePageNo.value = 1
    loadInstances()
  }, 250)
}

const handleInstancePageSizeChange = (size: number) => {
  instancePageSize.value = size
  instancePageNo.value = 1
  loadInstances()
}

const getModelName = (modelId: string) => {
  const model = findModelById(modelId)
  return model ? model.modelName : modelId
}

const getModelAttributes = (modelId: string) => {
  const model = findModelById(modelId)
  return model?.capabilitySpec?.attributes || []
}

const getAttributeName = (key: string) => {
  if (!activeInstance.value) return key
  const attr = getModelAttributes(activeInstance.value.modelId).find((v: any) => [v.identifier, v.name, v.displayName].filter(Boolean).map(String).includes(String(key)))
  return attr?.displayName || attr?.name || key
}

function asArray<T = any>(value: any): T[] {
  return Array.isArray(value) ? value : []
}

const paramKey = (param: any) => String(param.name || param.paramName || param.displayName || '')
const normalizeType = (type: any) => String(type || '').toUpperCase()
const isBooleanType = (type: any) => normalizeType(type) === 'BOOLEAN' || normalizeType(type) === 'BOOL'
const isIntegerType = (type: any) => ['INTEGER', 'INT', 'LONG'].includes(normalizeType(type))
const isNumberType = (type: any) => isIntegerType(type) || ['DOUBLE', 'FLOAT', 'NUMBER', 'DECIMAL'].includes(normalizeType(type))

const defaultValueForType = (type: any) => {
  if (isBooleanType(type)) return false
  if (isNumberType(type)) return 0
  return ''
}

const resetControlParams = () => {
  const next: Record<string, any> = {}
  activeControlParams.value.forEach((param: any) => {
    next[paramKey(param)] = controlParamValues.value[paramKey(param)] ?? defaultValueForType(param.dataType)
  })
  controlParamValues.value = next
}

const buildControlParameters = () => {
  const parameters: Record<string, any> = {}
  activeControlParams.value.forEach((param: any) => {
    const key = paramKey(param)
    let value = controlParamValues.value[key]
    if (isIntegerType(param.dataType)) value = value === '' || value == null ? 0 : Number.parseInt(String(value), 10)
    else if (isNumberType(param.dataType)) value = value === '' || value == null ? 0 : Number(value)
    parameters[key] = value
  })
  return parameters
}

const viewDetails = (instance: DeviceInstance) => {
  activeInstance.value = JSON.parse(JSON.stringify(instance))
  if (!activeInstance.value.commConfig) {
    activeInstance.value.commConfig = {}
  }
  activeInstance.value.boundAdapterName ||= activeInstance.value.commConfig.boundAdapterName || ''
  activeInstance.value.boundDevicePoint ||= activeInstance.value.commConfig.boundDevicePoint || ''
  activeInstance.value.commConfig.mqttTopic = commandTopicPreview(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
  activeInstance.value.commConfig.mqttTopics = mqttTopicRows(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
  localConstraints.value = JSON.parse(JSON.stringify(activeInstance.value.commConfig.constraints || []))
  loadDevicePoints(activeInstance.value.boundAdapterName, 'drawer')
  datasetCreateForm.value = { templateId: '', dataDesc: '' }
  controlCommandId.value = activeInstanceCommands.value[0]?.commandId || ''
  resetControlParams()
  activeTab.value = 'info'
  drawerVisible.value = true
  loadDataSets()
  loadComponents()
}

const onModelChangeInDrawer = (modelId: string) => {
  if (!activeInstance.value) return
  activeInstance.value.stateMachineId = `${modelId}StateMachine`
  activeInstance.value.boundDevicePoint = ''
  loadDevicePoints(activeInstance.value.boundAdapterName || '', 'drawer')
}

const saveInstance = async () => {
  if (!activeInstance.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    payload.boundAdapterName = activeInstance.value.boundAdapterName
    payload.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.commConfig.boundAdapterName = activeInstance.value.boundAdapterName
    payload.commConfig.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.commConfig.mqttTopic = commandTopicPreview(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
    payload.commConfig.mqttTopics = mqttTopicRows(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
    payload.commConfig.constraints = localConstraints.value
    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('保存成功')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const deleteInstance = async (id: string) => {
  try {
    const res = await axios.delete(`/api/device/instance/delete/${id}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      drawerVisible.value = false
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

const addConstraint = () => {
  localConstraints.value.push({ targetAttr: '', operator: '>', threshold: '' })
}

const removeConstraint = (index: number) => {
  localConstraints.value.splice(index, 1)
}

const saveConstraints = async () => {
  if (!activeInstance.value) return
  saving.value = true
  try {
    const payload = JSON.parse(JSON.stringify(activeInstance.value))
    payload.boundAdapterName = activeInstance.value.boundAdapterName
    payload.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.commConfig.boundAdapterName = activeInstance.value.boundAdapterName
    payload.commConfig.boundDevicePoint = activeInstance.value.boundDevicePoint
    payload.commConfig.mqttTopic = commandTopicPreview(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
    payload.commConfig.mqttTopics = mqttTopicRows(activeInstance.value.boundAdapterName, activeInstance.value.boundDevicePoint)
    payload.commConfig.constraints = localConstraints.value.filter(v => v.targetAttr && v.threshold)
    const res = await axios.post('/api/device/instance/save', payload)
    if (res.data?.success) {
      ElMessage.success('约束保存成功')
      await loadData()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const fetchSnapshot = async () => {
  if (!activeInstance.value) return
  try {
    const res = await axios.get(`/api/device/instance/snapshot/${activeInstance.value.instanceId}`)
    if (res.data?.success) {
      snapshot.value = res.data.data || null
      lastSnapshotTime.value = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    }
  } catch {
    snapshot.value = null
  }
}

const startPolling = () => {
  stopPolling()
  fetchSnapshot()
  pollingTimer = setInterval(fetchSnapshot, 3000)
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

const closeDrawer = () => {
  stopPolling()
  snapshot.value = null
  activeInstance.value = null
  controlCommandId.value = ''
  controlParamValues.value = {}
  instanceDataSets.value = []
}

const loadComponents = async () => {
  if (!activeInstance.value) return
  loadingComponents.value = true
  try {
    const res = await axios.get('/api/device/component/list', { params: { parentInstanceId: activeInstance.value.instanceId } })
    if (res.data?.success) instanceComponents.value = res.data.data || []
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载结构拓扑失败')
  } finally {
    loadingComponents.value = false
  }
}

const saveComponent = async () => {
  if (!activeInstance.value || !componentForm.value.componentName.trim()) {
    ElMessage.warning('\u8bf7\u8f93\u5165\u7ec4\u4ef6\u540d\u79f0')
    return
  }
  savingComponent.value = true
  try {
    const res = await axios.post('/api/device/component/save', {
      componentName: componentForm.value.componentName.trim(),
      categoryId: componentForm.value.categoryId ? Number(componentForm.value.categoryId) : null,
      parentInstanceId: Number(activeInstance.value.instanceId),
      selfInstanceId: null,
      status: '\u672a\u914d\u7f6e',
      specification: {}
    })
    if (res.data?.success) {
      componentForm.value = { componentName: '', categoryId: '', selfInstanceId: '', status: '\u672a\u914d\u7f6e' }
      await loadComponents()
      ElMessage.success('\u7ec4\u4ef6\u69fd\u4f4d\u5df2\u65b0\u589e')
    } else {
      ElMessage.error(res.data?.message || '\u4fdd\u5b58\u7ec4\u4ef6\u5931\u8d25')
    }
  } finally {
    savingComponent.value = false
  }
}

const componentSpecBrief = (value: any) => {
  if (!value || (typeof value === 'object' && Object.keys(value).length === 0)) return '-'
  const text = typeof value === 'string' ? value : JSON.stringify(value)
  return text.length > 80 ? text.slice(0, 77) + '...' : text
}
const componentStatusType = (status?: string) => {
  if (status === '\u4f7f\u7528\u4e2d') return 'success'
  if (status === '\u5df2\u66f4\u6362') return 'warning'
  if (status === '\u5df2\u5e9f\u5f03') return 'info'
  return 'info'
}
const openComponentAction = (row: any, mode: 'configure' | 'replace') => {
  activeComponent.value = row
  componentActionMode.value = mode
  componentActionForm.value = {
    componentName: row?.componentName || '',
    selfInstanceId: row?.selfInstanceId ? String(row.selfInstanceId) : '',
    specificationText: row?.specification && Object.keys(row.specification || {}).length ? JSON.stringify(row.specification, null, 2) : '{}'
  }
  componentActionVisible.value = true
}
const submitComponentAction = async () => {
  if (!activeComponent.value?.id) return
  let specification: any = {}
  try {
    specification = componentActionForm.value.specificationText?.trim() ? JSON.parse(componentActionForm.value.specificationText) : {}
  } catch {
    ElMessage.error('\u89c4\u683c\u4fe1\u606f\u5fc5\u987b\u662f\u5408\u6cd5 JSON')
    return
  }
  savingComponent.value = true
  try {
    const payload = {
      componentName: componentActionForm.value.componentName,
      selfInstanceId: componentActionForm.value.selfInstanceId ? Number(componentActionForm.value.selfInstanceId) : null,
      specification
    }
    const action = componentActionMode.value === 'replace' ? 'replace' : 'configure'
    const res = await axios.post('/api/device/component/' + activeComponent.value.id + '/' + action, payload)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '\u4fdd\u5b58\u7ec4\u4ef6\u5931\u8d25')
      return
    }
    componentActionVisible.value = false
    await loadComponents()
    ElMessage.success(componentActionMode.value === 'replace' ? '\u7ec4\u4ef6\u5df2\u66f4\u6362' : '\u7ec4\u4ef6\u5df2\u914d\u7f6e')
  } finally {
    savingComponent.value = false
  }
}
const discardComponent = async (row: any) => {
  if (!row?.id) return
  try {
    await ElMessageBox.confirm('\u786e\u8ba4\u5c06\u8be5\u7ec4\u4ef6\u6807\u8bb0\u4e3a\u5df2\u5e9f\u5f03\uff1f', '\u5e9f\u5f03\u7ec4\u4ef6', { type: 'warning' })
    const res = await axios.post('/api/device/component/' + row.id + '/discard')
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '\u5e9f\u5f03\u7ec4\u4ef6\u5931\u8d25')
      return
    }
    await loadComponents()
    ElMessage.success('\u7ec4\u4ef6\u5df2\u5e9f\u5f03')
  } catch (error: any) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error.response?.data?.message || '\u5e9f\u5f03\u7ec4\u4ef6\u5f02\u5e38')
  }
}

const showComponentHistory = async (row: any) => {
  if (!row?.id) return
  componentHistoryVisible.value = true
  loadingComponentHistory.value = true
  try {
    const res = await axios.get('/api/device/component/' + row.id + '/history')
    if (res.data?.success) {
      componentHistoryRows.value = res.data.data || []
    } else {
      componentHistoryRows.value = []
      ElMessage.error(res.data?.message || '\u52a0\u8f7d\u7ec4\u4ef6\u5386\u53f2\u5931\u8d25')
    }
  } catch (error: any) {
    componentHistoryRows.value = []
    ElMessage.error(error.response?.data?.message || '\u52a0\u8f7d\u7ec4\u4ef6\u5386\u53f2\u5931\u8d25')
  } finally {
    loadingComponentHistory.value = false
  }
}

const deleteComponent = async (row: any) => {
  const id = row?.id
  if (!id) return
  const res = await axios.delete('/api/device/component/delete/' + id)
  if (res.data?.success) {
    ElMessage.success('组件已删除')
    await loadComponents()
  } else {
    ElMessage.error(res.data?.message || '删除组件失败')
  }
}

const loadDataSets = async () => {
  if (!activeInstance.value) return
  loadingDataSets.value = true
  try {
    const res = await axios.get('/api/data/index/list', {
      params: { deviceInstanceId: activeInstance.value.instanceId }
    })
    if (res.data?.success) {
      instanceDataSets.value = res.data.data || []
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '加载数据集失败')
  } finally {
    loadingDataSets.value = false
  }
}

const createDataSetForInstance = async () => {
  if (!activeInstance.value) return
  if (!datasetCreateForm.value.templateId) {
    ElMessage.warning('请选择数据模板')
    return
  }
  creatingDataSet.value = true
  try {
    const template = dataTemplates.value.find(tpl => String(templateIdOf(tpl)) === String(datasetCreateForm.value.templateId))
    const res = await axios.post('/api/data/index/create-dataset', {
      templateId: datasetCreateForm.value.templateId,
      deviceInstanceId: activeInstance.value.instanceId,
      dataDesc: datasetCreateForm.value.dataDesc || (activeInstance.value.instanceName + ' - ' + (template?.templateName || '自定义数据表'))
    })
    if (res.data?.success) {
      ElMessage.success('数据表创建成功')
      datasetCreateForm.value = { templateId: '', dataDesc: '' }
      await loadDataSets()
    } else {
      ElMessage.error(res.data?.message || '创建失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '创建失败')
  } finally {
    creatingDataSet.value = false
  }
}

const deleteInstanceDataSet = async (row: any) => {
  const id = row?.id || row?.dataIndexId
  if (!id) return
  try {
    const res = await axios.delete('/api/data/index/delete/' + id)
    if (res.data?.success) {
      ElMessage.success('数据表已删除')
      await loadDataSets()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '删除失败')
  }
}

watch(activeTab, (tab) => {
  if (tab === 'status') {
    startPolling()
  } else {
    stopPolling()
  }
})


const commandTopicPreview = (adapterName?: string, devicePoint?: string) => {
  if (!adapterName || !devicePoint) return ''
  return `smartlab/adapter/${adapterName}/${devicePoint}/command`
}

const updateCreateTopicPreview = () => {
  createForm.value.mqttTopicPreview = commandTopicPreview(createForm.value.boundAdapterName, createForm.value.boundDevicePoint)
}

const devicePointLabel = (point: AdapterDevicePoint) => {
  return point.description ? `${point.devicePoint} · ${point.description}` : point.devicePoint
}

const onModelChangeInCreate = async () => {
  const adapterName = selectedCreateModel.value?.capabilitySpec?.adapterContract?.config?.adapterName || createForm.value.boundAdapterName
  createForm.value.boundAdapterName = adapterName || ''
  createForm.value.boundDevicePoint = ''
  await loadDevicePoints(createForm.value.boundAdapterName, 'create')
  updateCreateTopicPreview()
}

const onAdapterChangeInCreate = async () => {
  createForm.value.boundDevicePoint = ''
  await loadDevicePoints(createForm.value.boundAdapterName, 'create')
  updateCreateTopicPreview()
}

const onAdapterChangeInDrawer = async () => {
  if (!activeInstance.value) return
  activeInstance.value.boundDevicePoint = ''
  await loadDevicePoints(activeInstance.value.boundAdapterName || '', 'drawer')
}

watch(() => [createForm.value.boundAdapterName, createForm.value.boundDevicePoint], () => {
  updateCreateTopicPreview()
})

const openCreateDialog = () => {
  createForm.value = {
    instanceName: '',
    modelId: '',
    boundAdapterName: '',
    boundDevicePoint: '',
    mqttTopicPreview: '',
    assetInfo: { serialNumber: '', purchaseDate: '', installDate: '', location: '', notes: '' }
  }
  createDialogVisible.value = true
  if (!models.value.length) {
    searchModels('')
  }
  if (!adapterOptions.value.length) {
    loadAdapters()
  }
}
const submitCreate = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      const mqttTopic = commandTopicPreview(createForm.value.boundAdapterName, createForm.value.boundDevicePoint)
      const payload: any = {
        instanceId: '',
        modelId: createForm.value.modelId,
        stateMachineId: createForm.value.modelId + 'StateMachine',
        instanceName: createForm.value.instanceName,
        boundAdapterName: createForm.value.boundAdapterName,
        boundDevicePoint: createForm.value.boundDevicePoint,
        instanceConfig: { assetInfo: createForm.value.assetInfo },
        assetInfo: createForm.value.assetInfo,
        commConfig: {
          boundAdapterName: createForm.value.boundAdapterName,
          boundDevicePoint: createForm.value.boundDevicePoint,
          mqttTopic,
          mqttTopics: mqttTopicRows(createForm.value.boundAdapterName, createForm.value.boundDevicePoint),
          constraints: []
        },
        isOnline: false
      }
      const res = await axios.post('/api/device/instance/save', payload)
      if (res.data?.success) {
        ElMessage.success('\u8bbe\u5907\u6dfb\u52a0\u6210\u529f')
        createDialogVisible.value = false
        await loadData()
      } else {
        ElMessage.error(res.data?.message || '\u6dfb\u52a0\u5931\u8d25')
      }
    } catch (err: any) {
      ElMessage.error(err.response?.data?.message || '\u6dfb\u52a0\u5931\u8d25')
    } finally {
      creating.value = false
    }
  })
}
const sendManualCommand = async () => {
  if (!activeInstance.value) return
  if (!controlCommandId.value) {
    ElMessage.warning('\u8bf7\u9009\u62e9\u547d\u4ee4')
    return
  }
  const parameters = buildControlParameters()
  sendingControl.value = true
  try {
    const res = await axios.post('/api/device/instance/control/' + activeInstance.value.instanceId, {
      commandId: controlCommandId.value,
      parameters
    })
    if (res.data?.success) {
      ElMessage.success('\u6307\u4ee4\u6d88\u606f\u5df2\u751f\u6210')
    } else {
      ElMessage.error(res.data?.message || '\u6307\u4ee4\u53d1\u9001\u5931\u8d25')
    }
  } catch (err: any) {
    ElMessage.error(err.response?.data?.message || '\u6307\u4ee4\u53d1\u9001\u5931\u8d25')
  } finally {
    sendingControl.value = false
  }
}

const categoryNameById = (id: any) => categoriesMap.value[String(id)] || ''
const instanceNameById = (id: any) => instances.value.find(item => String(item.instanceId) === String(id))?.instanceName || (id ? String(id) : '-')


function normalizeModel(raw: any): DeviceModel {
  return {
    ...raw,
    modelId: String(raw.modelId || raw.id || ''),
    modelName: raw.modelName || raw.name || '',
    deviceCategory: raw.deviceCategory || raw.categoryName || '',
    capabilitySpec: raw.capabilitySpec || {
      attributes: raw.attributes || [],
      capabilities: raw.capabilities || [],
      adapterContract: raw.adapterContract || { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: [] }
    }
  }
}

function normalizeInstance(raw: any): DeviceInstance {
  const commConfig = raw.commConfig || raw.instanceConfig || {}
  const boundAdapterName = raw.boundAdapterName || commConfig.boundAdapterName || commConfig.adapterName || ''
  const boundDevicePoint = raw.boundDevicePoint || commConfig.boundDevicePoint || commConfig.devicePoint || ''
  return {
    ...raw,
    instanceId: String(raw.instanceId || raw.id || ''),
    modelId: String(raw.modelId || raw.deviceModelId || ''),
    stateMachineId: raw.stateMachineId || `${raw.modelId || raw.deviceModelId || ''}StateMachine`,
    instanceName: raw.instanceName || raw.name || '',
    boundAdapterName,
    boundDevicePoint,
    commConfig: { ...commConfig, boundAdapterName, boundDevicePoint, mqttTopic: commConfig.mqttTopic || commandTopicPreview(boundAdapterName, boundDevicePoint), mqttTopics: commConfig.mqttTopics || mqttTopicRows(boundAdapterName, boundDevicePoint) },
    isOnline: raw.isOnline === true || raw.onlineStatus === 'ONLINE'
  }
}

const templateIdOf = (template: any) => String(template?.templateId || template?.id || '')

function findModelById(modelId: string) {
  if (!modelId) return null
  return modelOptions.value.find(v => v.modelId === modelId) || models.value.find(v => v.modelId === modelId) || null
}

onMounted(loadData)
onUnmounted(() => {
  stopPolling()
  if (modelSearchTimer) window.clearTimeout(modelSearchTimer)
  if (instanceSearchTimer) window.clearTimeout(instanceSearchTimer)
})
</script>

<style scoped>
.instance-page {
  height: calc(100vh - 52px);
  background: #eef2f6;
}

.layout { height: 100%; }

.sidebar {
  background: #fff;
  border-right: 1px solid #ccd6e3;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 14px 16px;
  border-bottom: 1px solid #e5e7eb;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #1f2937;
}

.sidebar-scroll { flex: 1; }

.model-list { padding: 10px; }

.model-item {
  border: 1px solid #e5e7eb;
  background: #f9fafb;
  border-radius: 6px;
  padding: 10px;
  margin-bottom: 8px;
  cursor: pointer;
}

.model-item.active {
  border-color: #9ca3af;
  background: #eef1f5;
}

.model-name { font-size: 13px; font-weight: 600; color: #111827; }
.model-meta { font-size: 11px; color: #6b7280; margin-top: 4px; }

.content { padding: 0; background: #eef2f6; display: flex; flex-direction: column; }

.main-header {
  padding: 16px 20px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.main-header h2 { margin: 0; font-size: 19px; color: #111827; }
.subtitle { font-size: 12px; color: #6b7280; }
.actions { display: flex; gap: 8px; align-items: center; }
.add-device-trigger { height: 34px; border-radius: 18px; padding: 0 16px; box-shadow: 0 8px 18px rgba(37, 99, 235, 0.18); }
.instance-create-body { padding: 2px 4px 0; }
.instance-create-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; }
.instance-create-form :deep(.el-form-item) { margin-bottom: 8px; }
.instance-create-form :deep(.el-select) { width: 100%; }
.instance-create-form :deep(.el-form-item:nth-last-child(1)) { grid-column: 1 / -1; }
.component-create-panel { margin-bottom: 10px; padding: 10px; border: 1px solid #dbe4ef; border-radius: 6px; background: #f8fafc; display: grid; grid-template-columns: 1.1fr 1fr 1fr 110px auto; gap: 8px; align-items: center; }

.instance-search {
  width: 220px;
}

.stats-bar {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
  padding: 12px 20px;
  background: #f9fafb;
  border-bottom: 1px solid #e5e7eb;
}

.stat-item {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
}

.stat-item strong { font-size: 18px; color: #111827; }

.card-scroll { flex: 1; }

.instance-pagination {
  display: flex;
  justify-content: flex-end;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
}

.card-grid {
  padding: 16px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 12px;
}

.instance-card {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  padding: 12px;
  cursor: pointer;
}

.instance-card:hover { border-color: #9ca3af; }

.card-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.name { font-size: 14px; font-weight: 600; color: #111827; }

.line {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #4b5563;
  margin-bottom: 6px;
  gap: 8px;
}

.mono {
  font-family: 'Consolas', 'Menlo', monospace;
  color: #111827;
}

.mono-textarea :deep(textarea) {
  font-family: 'Consolas', 'Menlo', monospace;
  font-size: 12px;
}

.empty-wrap { padding: 80px 0; }

.drawer-body { padding: 0 4px; }

.footer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.status-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 10px;
}

.attr-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attr-row {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 12px;
}

.empty-inline {
  color: #9ca3af;
  font-size: 12px;
}

.constraint-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.dataset-create-panel {
  display: grid;
  grid-template-columns: minmax(180px, 0.9fr) minmax(220px, 1.1fr) auto;
  gap: 8px;
  align-items: center;
  margin-bottom: 10px;
  padding: 10px;
  border: 1px solid #ccd6e3;
  border-left: 3px solid #2563eb;
  border-radius: 6px;
  background: #f8fafc;
}

.drawer-body :deep(.el-tabs__content) {
  padding-top: 8px;
}

.drawer-body :deep(.el-table th) {
  background: #f3f6fa;
  color: #243244;
}

.mb-12 { margin-bottom: 12px; }
.mt-12 { margin-top: 12px; }

.instance-list-wrap { flex: 1; min-height: 0; padding: 12px; overflow: auto; }
.instance-table { cursor: pointer; }
.instance-table :deep(.el-table__row:hover) { background: #eef6ff; }
.instance-name-cell { display: grid; gap: 3px; }
.instance-name-cell strong { color: #0f172a; font-size: 14px; }
.instance-name-cell span { color: #64748b; font-size: 12px; }
.binding-cell { display: grid; grid-template-columns: 1fr; gap: 4px; }
.binding-cell span { display: grid; grid-template-columns: 58px minmax(0, 1fr); gap: 8px; align-items: center; }
.binding-cell b { color: #64748b; font-weight: 700; }
.topic-cell { display: grid; gap: 3px; }
.topic-cell span { display: grid; grid-template-columns: 42px minmax(0, 1fr); gap: 8px; align-items: center; }
.topic-cell b { color: #64748b; font-weight: 700; }
.topic-cell code, .topic-table code, .topic-preview-grid code { display: block; overflow: hidden; color: #0f172a; font-family: Consolas, Menlo, monospace; text-overflow: ellipsis; white-space: nowrap; }
.instance-detail-dialog :deep(.el-dialog__body) { padding: 8px 14px 14px; }
.drawer-body { padding: 0; }
.instance-info-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; }
.instance-info-grid :deep(.el-form-item) { margin-bottom: 2px; }
.readonly-status { height: 32px; display: flex; align-items: center; gap: 8px; color: #64748b; font-size: 12px; }
.topic-section, .snapshot-section { margin-top: 10px; border: 1px solid #cbd5e1; }
.section-caption { height: 34px; padding: 0 10px; border-bottom: 1px solid #dbe4ef; background: #f8fafc; display: flex; align-items: center; color: #0f172a; font-weight: 800; }
.manual-control-form { display: grid; gap: 10px; }
.control-param-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px 12px; }
.control-param-grid :deep(.el-form-item) { margin-bottom: 0; }
.field-hint { margin-top: 3px; color: #64748b; font-size: 12px; }
.topic-preview-grid { width: 100%; border: 1px solid #cbd5e1; }
.topic-preview-grid div { display: grid; grid-template-columns: 72px minmax(0, 1fr); gap: 10px; min-height: 32px; padding: 6px 8px; border-bottom: 1px solid #e2e8f0; align-items: center; }
.topic-preview-grid div:last-child { border-bottom: 0; }
.topic-preview-grid span { color: #64748b; font-weight: 700; }
.form-wide { grid-column: 1 / -1; }
@media (max-width: 960px) {
  .main-header {
    align-items: flex-start;
    gap: 12px;
  }

  .actions {
    flex-wrap: wrap;
    justify-content: flex-end;
  }

  .instance-search {
    width: 180px;
  }

  .stats-bar {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>

````

---

## Frontend/src/views/device/DeviceModelManagement.vue

````text
<template>
  <div class="model-management-page">
    <aside class="model-tree-pane">
      <div class="pane-title">
        <div>
          <strong>设备类别与模型</strong>
          <span>类别树 / 模型挂载</span>
        </div>
        <el-button size="small" type="primary" @click="beginCreateRootCategory">新增根类别</el-button>
      </div>
      <el-input v-model="keyword" size="small" clearable placeholder="搜索类别或模型" class="tree-search" />

      <div v-if="categoryEditor.visible" class="tree-editor">
        <div class="editor-caption">{{ categoryEditor.id ? '重命名类别' : '新增类别' }}</div>
        <el-input v-model="categoryEditor.categoryName" size="small" placeholder="类别名称" />
        <el-input v-model="categoryEditor.description" size="small" placeholder="类别描述" />
        <div class="editor-actions">
          <el-button size="small" @click="categoryEditor.visible = false">取消</el-button>
          <el-button size="small" type="primary" @click="saveCategory">保存</el-button>
        </div>
      </div>

      <div class="tree-scroll" v-loading="loading">
        <el-tree
          :data="filteredTree"
          node-key="key"
 :default-expanded-keys="treeDefaultExpandedKeys"
          :expand-on-click-node="false"
          @node-click="handleTreeNodeClick"
        >
          <template #default="{ data }">
            <div class="tree-node" :class="{ active: activeNodeKey === data.key, model: data.type === 'model' }">
              <span class="node-main">
                <el-icon v-if="data.type === 'category'"><Folder /></el-icon>
                <el-icon v-else><Document /></el-icon>
                <span class="node-label">{{ data.label }}</span>
              </span>
              <span class="node-actions" v-if="data.type === 'category'">
                <el-button text size="small" @click.stop="beginCreateChildCategory(data)">子类</el-button>
                <el-button text size="small" @click.stop="beginCreateModel(data)">模型</el-button>
                <el-button text size="small" @click.stop="beginRenameCategory(data)">改名</el-button>
                <el-popconfirm title="确认删除该类别？仅空类别可删除" @confirm="deleteCategory(data)">
                  <template #reference><el-button text size="small" class="danger" @click.stop>删除</el-button></template>
                </el-popconfirm>
              </span>
            </div>
          </template>
        </el-tree>
        <el-empty v-if="!filteredTree.length" description="暂无类别或模型" />
      </div>
    </aside>

    <section class="model-detail-pane">
      <div class="detail-toolbar">
        <div class="toolbar-title">
          <span>{{ selectedCategoryName }}</span>
          <h1>{{ draft.modelName || '未选择设备模型' }}</h1>
        </div>
        <div class="toolbar-actions" v-if="draftReady">
          <el-button @click="loadSelectedModel" :disabled="!draft.modelId">还原</el-button>
          <el-button @click="previewModel">预览模型文件</el-button>
          <el-button type="primary" :loading="saving" @click="saveModel">保存模型</el-button>
          <el-popconfirm v-if="draft.modelId" title="确认删除该模型？有实例时后端会阻止删除" @confirm="deleteModel">
            <template #reference><el-button type="danger" plain>删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>

      <div v-if="!draftReady" class="empty-workspace">
        <el-empty description="请选择模型，或在左侧叶子类别下新建设备模型" />
      </div>

      <div v-else class="detail-body">
        <nav class="section-nav">
          <button v-for="item in navItems" :key="item.key" :class="{ active: activeSection === item.key }" @click="scrollToSection(item.key)">{{ item.label }}</button>
        </nav>

        <main ref="contentRef" class="section-scroll" @scroll="syncActiveSection">
          <section :ref="setSectionRef('basic')" class="editor-section">
            <SectionHeader title="基础信息" count-label="必填" />
            <div class="form-grid two">
              <el-form-item label="模型名称">
                <el-input v-model="draft.modelName" placeholder="例如：反应釜模型" />
              </el-form-item>
              <el-form-item label="所属类别">
                <el-select v-model="draft.categoryId" filterable placeholder="请选择叶子类别">
                  <el-option v-for="cat in leafCategories" :key="cat.id" :value="cat.id" :label="categoryPath(cat.id)" />
                </el-select>
              </el-form-item>
            </div>
          </section>

          <section :ref="setSectionRef('capability')" class="editor-section">
            <SectionHeader title="属性功能" :count-label="draft.attributes.length + draft.capabilities.length + ' 项'" />
            <div class="sub-section-title">
              <strong>设备属性</strong>
              <el-button size="small" type="primary" @click="addAttribute">新增属性</el-button>
            </div>
            <div class="dense-table">
              <div class="table-head attr-grid"><span>属性名</span><span>取值类型</span><span>数据类型</span><span>单位</span><span></span></div>
              <div v-for="(attr, idx) in draft.attributes" :key="attr._key" class="table-row attr-grid">
                <el-input v-model="attr.displayName" placeholder="显示名称" @input="syncDefaultTemplateFromAttributes" />
                <el-select v-model="attr.valueKind"><el-option label="连续值" value="CONTINUOUS" /><el-option label="离散值" value="DISCRETE" /></el-select>
                <el-select v-model="attr.dataType" @change="syncDefaultTemplateFromAttributes"><el-option v-for="type in dataTypes" :key="type" :label="type" :value="type" /></el-select>
                <el-input v-model="attr.unit" placeholder="单位" @input="syncDefaultTemplateFromAttributes" />
                <el-button text type="danger" @click="removeAttribute(idx)">删除</el-button>
              </div>
            </div>

            <div class="sub-section-title split-title">
              <strong>设备操作</strong>
              <el-button size="small" type="primary" @click="addCapability">新增操作</el-button>
            </div>
            <div class="capability-list">
              <article v-for="(cap, cIdx) in draft.capabilities" :key="cap._key" class="capability-item">
                <div class="capability-title">
                  <span class="index-badge">{{ cIdx + 1 }}</span>
                  <el-input v-model="cap.displayName" placeholder="操作名称" />
                  <el-button text type="danger" @click="removeCapability(cIdx)">删除</el-button>
                </div>
                <div class="param-grid">
                  <div class="param-head"><span>参数名</span><span>数据类型</span><span></span></div>
                  <div v-for="(param, pIdx) in cap.parameters" :key="param._key" class="param-row">
                    <el-input v-model="param.displayName" placeholder="参数显示名" />
                    <el-select v-model="param.dataType"><el-option v-for="type in dataTypes" :key="type" :label="type" :value="type" /></el-select>
                    <el-button text type="danger" @click="cap.parameters.splice(pIdx, 1)">删除</el-button>
                  </div>
                </div>
                <el-button size="small" plain @click="addCapabilityParam(cap)">新增参数</el-button>
              </article>
            </div>
          </section>

          <section :ref="setSectionRef('ports')" class="editor-section">
            <SectionHeader title="端口" :count-label="draft.ports.length + ' 个'" />
            <div class="sub-section-title">
              <strong>端口配置</strong>
              <el-button size="small" type="primary" @click="addPort">新增端口</el-button>
            </div>
            <div class="port-grid">
              <article v-for="(port, idx) in draft.ports" :key="port._key" class="line-card">
                <label>端口名称</label><el-input v-model="port.portName" placeholder="例如：温度输出" />
                <label>方向</label><el-select v-model="port.direction"><el-option label="输出" value="OUT" /><el-option label="输入" value="IN" /></el-select>
                <label>绑定属性</label><el-select v-model="port.bindingAttrName" clearable placeholder="请选择属性"><el-option v-for="attr in draft.attributes" :key="attr.name" :label="attr.displayName" :value="attr.name" /></el-select>
                <el-button text type="danger" @click="draft.ports.splice(idx, 1)">删除</el-button>
              </article>
            </div>
          </section>

          <section :ref="setSectionRef('adapter')" class="editor-section">
            <SectionHeader title="Adapter 契约" :count-label="draft.adapterContract?.config?.adapterName || '未选择'" />
            <div class="form-grid three">
              <el-form-item label="注册 Adapter">
                <el-select v-model="adapterSelection.adapterName" filterable clearable placeholder="选择已注册 Adapter" @change="onAdapterChanged">
                  <el-option v-for="adapter in adapters" :key="adapter.adapterName" :label="adapter.adapterName" :value="adapter.adapterName" />
                </el-select>
              </el-form-item>
              <el-form-item label="Adapter 类别">
                <el-select v-model="adapterSelection.categoryName" filterable clearable placeholder="选择 Adapter 类别" @change="loadAdapterContract">
                  <el-option v-for="cat in adapterCategories" :key="cat.categoryName" :label="cat.categoryName" :value="cat.categoryName" />
                </el-select>
              </el-form-item>
              <el-form-item label="协议">
                <el-input :model-value="draft.adapterContract?.config?.protocol || 'MQTT'" disabled />
              </el-form-item>
            </div>
            <div class="contract-panels">
              <InfoTable title="Adapter 属性" :rows="adapterAttributes" :columns="adapterAttrColumns" />
              <InfoTable title="Adapter 命令" :rows="adapterCommands" :columns="adapterCommandColumns" />
              <InfoTable title="Adapter 事件" :rows="adapterEvents" :columns="adapterEventColumns" />
            </div>
          </section>

          <section :ref="setSectionRef('mapping')" class="editor-section">
            <SectionHeader title="映射关系" count-label="模型 ↔ Adapter" />
            <div class="sub-section-title"><strong>属性映射</strong></div>
            <div class="mapping-list compact">
              <div v-for="mapping in attributeMappings" :key="mapping.adapterAttrName" class="mapping-row">
                <span class="mapping-object">{{ attrDisplay(mapping.modelAttributeName) || '未绑定属性' }}</span>
                <span class="arrow">→</span>
                <span class="mapping-object adapter">{{ mapping.adapterAttrName }}</span>
                <el-select v-model="mapping.modelAttributeName" clearable placeholder="模型属性"><el-option v-for="attr in availableAttrsForMapping(mapping)" :key="attr.name" :label="attr.displayName + ' / ' + attr.dataType" :value="attr.name" /></el-select>
              </div>
            </div>

            <div class="sub-section-title split-title"><strong>功能映射</strong></div>
            <div class="capability-map-list">
              <article v-for="cap in draft.capabilities" :key="cap._key" class="map-card">
                <div class="map-card-head">
                  <strong>{{ cap.displayName || '未命名操作' }}</strong>
                  <el-select v-model="cap.adapterCommandName" clearable placeholder="Adapter 命令" @change="ensureParamMappings(cap)">
                    <el-option v-for="cmd in availableCommandsForCapability(cap)" :key="cmd.commandName" :label="cmd.commandName" :value="cmd.commandName" />
                  </el-select>
                </div>
                <div v-if="cap.adapterCommandName" class="param-mapping-list">
                  <div v-for="row in cap.parameterMapping" :key="row._key" class="param-map-row">
                    <div class="field-block"><span>功能参数</span><el-select v-model="row.capabilityParamName" :disabled="row.isFixedValue" clearable placeholder="选择功能参数"><el-option v-for="param in availableCapabilityParams(cap, row)" :key="param.name" :label="param.displayName + ' / ' + param.dataType" :value="param.name" /></el-select></div>
                    <div class="field-block"><span>Adapter 参数</span><el-select v-model="row.commandParamName" clearable placeholder="选择命令参数"><el-option v-for="param in availableCommandParams(cap, row)" :key="param.paramName" :label="param.paramName + ' / ' + param.dataType" :value="param.paramName" /></el-select></div>
                    <div class="field-block small"><span>取值方式</span><el-switch v-model="row.isFixedValue" active-text="固定" inactive-text="映射" /></div>
                    <div v-if="row.isFixedValue" class="field-block"><span>固定值</span><el-input v-model="row.fixedValue" placeholder="输入固定值" /></div>
                    <el-button text type="danger" @click="removeParamMapping(cap, row)">删除</el-button>
                  </div>
                </div>
                <el-button size="small" plain @click="addParamMapping(cap)">新增参数映射</el-button>
              </article>
            </div>
          </section>

          <section :ref="setSectionRef('state')" class="editor-section">
            <SectionHeader title="状态机" :count-label="opStates.length + ' 个功能状态'" />
            <div class="sub-section-title"><strong>功能状态</strong><el-button size="small" type="primary" @click="addOpState">新增状态</el-button></div>
            <div class="state-grid">
              <div v-for="state in opStates" :key="state._key" class="state-pill" :class="{ initial: draft.opState.initialStateName === state.stateName }">
                <el-input v-model="state.stateName" placeholder="状态名" />
                <el-radio v-model="draft.opState.initialStateName" :label="state.stateName">初始</el-radio>
                <el-button text type="danger" @click="removeOpState(state)">删除</el-button>
              </div>
            </div>
            <div class="sub-section-title split-title"><strong>功能状态转移</strong><el-button size="small" type="primary" @click="addTransition">新增规则</el-button></div>
            <div class="transition-list">
              <div v-for="(tr, idx) in draft.stateTransitions" :key="tr._key" class="transition-row">
                <el-input v-model="tr.description" placeholder="说明" />
                <el-select v-model="tr.fromStateName" placeholder="来源"><el-option v-for="s in opStates" :key="s.stateName" :label="s.stateName" :value="s.stateName" /></el-select>
                <span class="arrow">→</span>
                <el-select v-model="tr.toStateName" placeholder="目标"><el-option v-for="s in opStates" :key="s.stateName" :label="s.stateName" :value="s.stateName" /></el-select>
                <el-select v-model="tr.trigger.interfaceName" placeholder="接口"><el-option v-for="itf in adapterInInterfaces" :key="itf.name" :label="itf.name" :value="itf.name" /></el-select>
                <el-select v-model="tr.trigger.signalName" filterable placeholder="接收信号"><el-option v-for="sig in adapterAllowedSignals" :key="sig" :label="sig" :value="sig" /></el-select>
                <el-button text type="danger" @click="draft.stateTransitions.splice(idx, 1)">删除</el-button>
              </div>
            </div>
          </section>

          <section :ref="setSectionRef('bom')" class="editor-section">
            <SectionHeader title="BOM" :count-label="draft.componentsBom.length + ' 个组件槽'" />
            <div class="sub-section-title"><strong>组件槽</strong><el-button size="small" type="primary" @click="addBomSlot">新增组件槽</el-button></div>
            <div class="dense-table">
              <div class="table-head bom-grid"><span>组件名称</span><span>组件类别</span><span>规格说明</span><span></span></div>
              <div v-for="(slot, idx) in draft.componentsBom" :key="slot._key" class="table-row bom-grid">
                <el-input v-model="slot.componentName" placeholder="例如：搅拌电机" />
                <el-select v-model="slot.categoryId" clearable filterable placeholder="组件类别"><el-option v-for="cat in categories" :key="cat.id" :label="categoryPath(cat.id)" :value="cat.id" /></el-select>
                <el-input v-model="slot.specificationText" placeholder="规格信息" />
                <el-button text type="danger" @click="draft.componentsBom.splice(idx, 1)">删除</el-button>
              </div>
            </div>
          </section>

          <section :ref="setSectionRef('data')" class="editor-section">
            <SectionHeader title="数据模板" :count-label="defaultDetails.length + ' 个字段'" />
            <div class="form-grid two">
              <el-form-item label="默认模板名称"><el-input v-model="draft.defaultDataTemplate.main.templateName" /></el-form-item>
              <el-form-item label="模板说明"><el-input v-model="draft.defaultDataTemplate.main.templateDesc" /></el-form-item>
            </div>
            <el-table :data="defaultDetails" border size="small" class="plain-table">
              <el-table-column prop="columnName" label="存储字段" min-width="160" />
              <el-table-column prop="columnDesc" label="字段说明" min-width="150" />
              <el-table-column label="绑定属性" min-width="150"><template #default="{ row }">{{ attrDisplay(row.deviceAttrKey) || '-' }}</template></el-table-column>
              <el-table-column prop="defaultValue" label="默认值" width="120"><template #default="{ row }">{{ row.defaultValue || '-' }}</template></el-table-column>
              <el-table-column label="字段用途" width="120"><template #default="{ row }">{{ isUnitField(row) ? '单位' : '采集值' }}</template></el-table-column>
            </el-table>
          </section>

          <section :ref="setSectionRef('file')" class="editor-section">
            <SectionHeader title="模型文件" count-label="后端预览" />
            <div class="json-toolbar"><el-button size="small" @click="previewModel">刷新预览</el-button></div>
            <pre class="json-preview">{{ modelPreviewText }}</pre>
          </section>
        </main>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, nextTick, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Document, Folder } from '@element-plus/icons-vue'

const dataTypes = ['STRING', 'INTEGER', 'DOUBLE', 'BOOLEAN']
const navItems = [
  { key: 'basic', label: '基础信息' },
  { key: 'capability', label: '属性功能' },
  { key: 'ports', label: '端口' },
  { key: 'adapter', label: 'Adapter 契约' },
  { key: 'mapping', label: '映射关系' },
  { key: 'state', label: '状态机' },
  { key: 'bom', label: 'BOM' },
  { key: 'data', label: '数据模板' },
  { key: 'file', label: '模型文件' }
]

const SectionHeader = defineComponent({
  props: { title: String, countLabel: String },
  setup(props) {
    return () => h('div', { class: 'section-head' }, [h('h2', props.title), props.countLabel ? h('span', props.countLabel) : null])
  }
})

const InfoTable = defineComponent({
  props: { title: String, rows: Array, columns: Array },
  setup(props) {
    return () => h('div', { class: 'info-table' }, [
      h('div', { class: 'info-table-title' }, [h('strong', props.title), h('span', `${props.rows?.length || 0} 项`)]),
      h('table', [
        h('thead', [h('tr', props.columns?.map(col => h('th', col.label)))]),
        h('tbody', (props.rows || []).length
          ? props.rows.map(row => h('tr', props.columns?.map(col => h('td', col.render ? col.render(row) : row[col.prop] || '-'))))
          : [h('tr', [h('td', { colspan: props.columns?.length || 1 }, '暂无数据')])])
      ])
    ])
  }
})

const loading = ref(false)
const saving = ref(false)
const keyword = ref('')
const categories = ref([])
const models = ref([])
const adapters = ref([])
const adapterCategories = ref([])
const activeNodeKey = ref('')
const activeSection = ref('basic')
const draftReady = ref(false)
const modelPreviewText = ref('')
const contentRef = ref(null)
const sectionRefs = reactive({})
let scrollTicking = false
const adapterSelection = reactive({ adapterName: '', categoryName: '' })
const categoryEditor = reactive({ visible: false, id: null, parentCategoryId: null, categoryName: '', description: '' })

const draft = reactive(emptyDraft())

const adapterAttrColumns = [{ label: '属性', prop: 'name' }, { label: '数据类型', prop: 'dataType' }, { label: '说明', prop: 'description' }]
const adapterCommandColumns = [{ label: '命令', prop: 'commandName' }, { label: '参数', render: row => (row.commandParameters || []).filter(p => !p.hidden).map(p => `${p.paramName} / ${p.dataType}`).join('、') || '-' }, { label: '说明', prop: 'description' }]
const adapterEventColumns = [{ label: '事件', prop: 'eventName' }, { label: '类型', prop: 'eventType' }, { label: '说明', prop: 'description' }]

const modelMap = computed(() => Object.fromEntries(models.value.map(m => [String(modelIdOf(m)), m])))
const childrenByCategory = computed(() => {
  const map = new Map()
  categories.value.forEach(cat => {
    const parent = cat.parentCategoryId == null ? 'root' : String(cat.parentCategoryId)
    if (!map.has(parent)) map.set(parent, [])
    map.get(parent).push(cat)
  })
  return map
})
const leafCategories = computed(() => categories.value.filter(cat => !childrenByCategory.value.has(String(cat.id))))
const selectedCategoryName = computed(() => draft.categoryId ? categoryPath(draft.categoryId) : '未绑定类别')
const categoryModelMap = computed(() => {
  const map = new Map()
  models.value.forEach(model => {
    const categoryId = model.categoryId == null ? 'uncategorized' : String(model.categoryId)
    if (!map.has(categoryId)) map.set(categoryId, [])
    map.get(categoryId).push(model)
  })
  return map
})
const treeData = computed(() => buildTree('root'))
const treeDefaultExpandedKeys = computed(() => treeData.value.slice(0, 6).map(node => node.key))
const filteredTree = computed(() => filterTree(treeData.value, keyword.value.trim().toLowerCase()))
const adapterAttributes = computed(() => asArray(draft.adapterContract?.telemetry?.adapterAttributes))
const adapterCommands = computed(() => asArray(draft.adapterContract?.commands))
const adapterEvents = computed(() => {
  const events = draft.adapterContract?.events || {}
  const cmd = asArray(events.cmdEvents).map(e => ({ eventName: e.eventName || e.name, description: e.description, eventType: '指令周期' }))
  const op = asArray(events.opEvents).map(e => ({ eventName: e.eventName || e.name, description: e.description, eventType: '业务事件' }))
  return [...cmd, ...op]
})
const attributeMappings = computed(() => asArray(draft.adapterContract?.telemetry?.attributesMapping))
const adapterAllowedSignals = computed(() => adapterEvents.value.map(e => e.eventName).filter(Boolean))
const adapterInInterfaces = computed(() => [{ name: 'Interface_adapter_in' }])
const opStates = computed(() => draft.opState.states)
const defaultDetails = computed(() => draft.defaultDataTemplate.details)

function emptyDraft() {
  return {
    modelId: null,
    modelName: '',
    categoryId: null,
    attributes: [],
    capabilities: [],
    adapterContract: { config: { protocol: 'MQTT' }, commands: [], telemetry: { adapterAttributes: [], attributesMapping: [] }, events: { cmdEvents: [], opEvents: [] } },
    ports: [],
    intrinsicConstraints: [],
    opState: { initialStateName: 'IDLE', states: [{ _key: uid(), stateName: 'IDLE', onEntry: [] }] },
    stateTransitions: [],
    componentsBom: [],
    defaultDataTemplate: { enabled: true, main: { templateName: '', templateDesc: '' }, details: [] }
  }
}

function resetDraft(next = emptyDraft()) {
  Object.keys(draft).forEach(key => delete draft[key])
  Object.assign(draft, next)
}

async function loadAll() {
 loading.value = true
 let firstModel = null
 try {
 const [catRes, modelRes, adapterRes] = await Promise.all([
 axios.get("/api/device/category/list"),
 axios.get("/api/device/model/list"),
 axios.get("/api/adapter/index/list").catch(() => ({ data: { data: [] } }))
 ])
 categories.value = asArray(catRes.data?.data)
 models.value = asArray(modelRes.data?.data)
 adapters.value = asArray(adapterRes.data?.data)
 if (!draftReady.value && models.value.length) firstModel = models.value[0]
 } catch (err) {
 ElMessage.error(errorMessage(err, "加载设备模型失败"))
 } finally {
 loading.value = false
 }
 if (firstModel) nextTick(() => selectModel(firstModel))
}

function buildTree(parentKey) {
  const categoryNodes = asArray(childrenByCategory.value.get(parentKey)).map(cat => ({
    key: 'cat_' + cat.id,
    type: 'category',
    label: cat.categoryName,
    data: cat,
    children: [...buildTree(String(cat.id)), ...modelNodesOf(cat.id)]
  }))
  return categoryNodes.sort((a, b) => a.label.localeCompare(b.label, 'zh-CN'))
}

function modelNodesOf(categoryId) {
  return asArray(categoryModelMap.value.get(String(categoryId))).map(model => ({ key: 'model_' + modelIdOf(model), type: 'model', label: model.modelName, data: model, children: [] }))
}

function filterTree(nodes, kw) {
  if (!kw) return nodes
  return nodes.map(node => {
    const children = filterTree(asArray(node.children), kw)
    const hit = String(node.label || '').toLowerCase().includes(kw)
    return hit || children.length ? { ...node, children } : null
  }).filter(Boolean)
}

async function handleTreeNodeClick(node) {
  activeNodeKey.value = node.key
  if (node.type === 'model') await selectModel(node.data)
}

async function selectModel(model) {
  activeNodeKey.value = 'model_' + modelIdOf(model)
  draftReady.value = true
  await loadModelBundle(modelIdOf(model))
}

async function loadSelectedModel() {
  if (draft.modelId) await loadModelBundle(draft.modelId)
}

async function loadModelBundle(id) {
  const res = await axios.get(`/api/device/model/${id}/bundle`)
  const bundle = res.data?.data || {}
  const capability = bundle.capabilityModel || {}
  const state = bundle.stateMachineModel || {}
  const metadata = capability.metadata || {}
  const next = emptyDraft()
  next.modelId = metadata.modelId || id
  next.modelName = metadata.modelName || modelMap.value[String(id)]?.modelName || ''
  next.categoryId = metadata.deviceCategoryId || modelMap.value[String(id)]?.categoryId || null
  next.attributes = withKeys(asArray(capability.attributes))
  next.capabilities = withKeys(asArray(capability.capabilities).map(cap => ({ ...cap, parameters: withKeys(asArray(cap.parameters)), parameterMapping: withKeys(asArray(cap.parameterMapping)) })))
  next.adapterContract = normalizeAdapterContract(capability.adapterContract)
  next.ports = withKeys(asArray(capability.ports))
  next.intrinsicConstraints = withKeys(asArray(capability.intrinsicConstraints))
  next.opState = normalizeOpState(state.opStateSpace)
  next.stateTransitions = withKeys(asArray(state.transitions).filter(t => !isStandardTransition(t)).map(t => ({ ...t, trigger: t.trigger || { interfaceName: 'Interface_adapter_in', signalName: '' }, actions: asArray(t.actions) })))
  next.componentsBom = withKeys(asArray(modelMap.value[String(id)]?.componentsBom || capability.componentsBom))
  next.defaultDataTemplate.main.templateName = `${next.modelName || '设备模型'} 默认数据模板`
  next.defaultDataTemplate.main.templateDesc = '系统根据设备模型自动生成的默认数据模板'
  resetDraft(next)
  adapterSelection.adapterName = next.adapterContract?.config?.adapterName || ''
  adapterSelection.categoryName = next.adapterContract?.config?.categoryName || ''
  if (adapterSelection.adapterName) await loadAdapterCategories(adapterSelection.adapterName)
  syncDefaultTemplateFromAttributes()
  modelPreviewText.value = '点击右上角预览按钮或本模块刷新预览后生成。'
}

function beginCreateModel(categoryNode) {
  const category = categoryNode.data
  if (!isLeafCategory(category.id)) {
    ElMessage.warning('只能在叶子类别下创建设备模型')
    return
  }
  draftReady.value = true
  activeNodeKey.value = categoryNode.key
  const next = emptyDraft()
  next.categoryId = category.id
  next.modelName = ''
  resetDraft(next)
  adapterSelection.adapterName = ''
  adapterSelection.categoryName = ''
  addAttribute()
  syncDefaultTemplateFromAttributes()
  nextTick(() => scrollToSection('basic'))
}

function beginCreateRootCategory() { Object.assign(categoryEditor, { visible: true, id: null, parentCategoryId: null, categoryName: '', description: '' }) }
function beginCreateChildCategory(node) { Object.assign(categoryEditor, { visible: true, id: null, parentCategoryId: node.data.id, categoryName: '', description: '' }) }
function beginRenameCategory(node) { Object.assign(categoryEditor, { visible: true, id: node.data.id, parentCategoryId: node.data.parentCategoryId ?? null, categoryName: node.data.categoryName || '', description: node.data.description || '' }) }

async function saveCategory() {
  if (!categoryEditor.categoryName.trim()) { ElMessage.warning('请输入类别名称'); return }
  const payload = { id: categoryEditor.id, categoryName: categoryEditor.categoryName.trim(), parentCategoryId: categoryEditor.parentCategoryId, description: categoryEditor.description }
  const res = await axios.post('/api/device/category/save', payload)
  if (res.data?.success) {
    ElMessage.success('类别已保存')
    categoryEditor.visible = false
    await loadAll()
  } else ElMessage.error(res.data?.message || '保存类别失败')
}

async function deleteCategory(node) {
  const res = await axios.delete(`/api/device/category/delete/${node.data.id}`)
  if (res.data?.success) { ElMessage.success('类别已删除'); await loadAll() } else ElMessage.error(res.data?.message || '删除失败')
}

async function saveModel() {
  try {
    ensureIdentifiers()
    if (!draft.modelName.trim()) throw new Error('请输入模型名称')
    if (!draft.categoryId) throw new Error('请选择所属叶子类别')
    saving.value = true
    const res = await axios.post('/api/device/model/save', buildPayload())
    if (res.data?.success) {
      ElMessage.success('模型已保存')
      const id = res.data.data?.modelId || res.data.data?.id || draft.modelId
      await loadAll()
      if (id) await loadModelBundle(id)
    } else ElMessage.error(res.data?.message || '保存失败')
  } catch (err) {
    ElMessage.error(err.message || errorMessage(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

async function deleteModel() {
  const res = await axios.delete(`/api/device/model/delete/${draft.modelId}`)
  if (res.data?.success) {
    ElMessage.success('模型已删除')
    draftReady.value = false
    resetDraft()
    await loadAll()
  } else ElMessage.error(res.data?.message || '删除失败')
}

async function previewModel(showMessage = true) {
  ensureIdentifiers()
  const res = await axios.post('/api/device/model/preview', buildPayload())
  if (res.data?.success) {
    modelPreviewText.value = JSON.stringify(res.data.data, null, 2)
    if (showMessage) ElMessage.success('预览已刷新')
  } else {
    modelPreviewText.value = JSON.stringify(buildPayload(), null, 2)
    if (showMessage) ElMessage.error(res.data?.message || '预览失败')
  }
}

function buildPayload() {
  ensureIdentifiers()
  return {
    modelId: draft.modelId,
    modelName: draft.modelName,
    categoryId: draft.categoryId,
    attributes: draft.attributes.map(({ _key, ...row }) => row),
    capabilities: draft.capabilities.map(cap => ({
      name: cap.name,
      displayName: cap.displayName,
      adapterCommandName: cap.adapterCommandName || '',
      parameters: cap.parameters.map(({ _key, ...p }) => p),
      parameterMapping: asArray(cap.parameterMapping).map(row => {
        const out = { commandParamName: row.commandParamName, capabilityParamName: row.isFixedValue ? undefined : row.capabilityParamName, isFixedValue: !!row.isFixedValue }
        if (row.isFixedValue) out.fixedValue = row.fixedValue
        return out
      })
    })),
    adapterContract: cleanAdapterContract(),
    ports: draft.ports.map(({ _key, ...row }) => row),
    intrinsicConstraints: draft.intrinsicConstraints.map(({ _key, ...row }) => row),
    opState: { initialStateName: draft.opState.initialStateName || 'IDLE', states: draft.opState.states.map(({ _key, ...state }) => ({ stateName: state.stateName, onEntry: asArray(state.onEntry) })) },
    stateTransitions: draft.stateTransitions.map(({ _key, ...row }) => row),
    componentsBom: draft.componentsBom.map(({ _key, specificationText, ...row }) => ({ ...row, specification: row.specification || (specificationText ? { description: specificationText } : {}) })),
    defaultDataTemplate: draft.defaultDataTemplate
  }
}

function cleanAdapterContract() {
  const contract = JSON.parse(JSON.stringify(draft.adapterContract || {}))
  contract.config = contract.config || { protocol: 'MQTT' }
  contract.config.protocol = contract.config.protocol || 'MQTT'
  contract.events = contract.events || { cmdEvents: [], opEvents: [] }
  contract.telemetry = contract.telemetry || { adapterAttributes: [], attributesMapping: [] }
  contract.telemetry.attributesMapping = asArray(contract.telemetry.attributesMapping).filter(row => row.adapterAttrName && row.modelAttributeName)
  contract.commands = asArray(contract.commands).map(cmd => ({ ...cmd, commandParameters: asArray(cmd.commandParameters).filter(p => !p.hidden) }))
  return contract
}

async function onAdapterChanged(name) {
  adapterSelection.categoryName = ''
  adapterCategories.value = []
  if (name) await loadAdapterCategories(name)
}

async function loadAdapterCategories(name) {
  const res = await axios.get(`/api/adapter/index/${encodeURIComponent(name)}/categories`)
  adapterCategories.value = asArray(res.data?.data)
}

async function loadAdapterContract() {
  if (!adapterSelection.adapterName || !adapterSelection.categoryName) return
  const res = await axios.get(`/api/adapter/index/${encodeURIComponent(adapterSelection.adapterName)}/adapter-contract`, { params: { categoryName: adapterSelection.categoryName } })
  if (res.data?.success) {
    draft.adapterContract = normalizeAdapterContract(res.data.data)
    draft.adapterContract.config.adapterName = adapterSelection.adapterName
    draft.adapterContract.config.categoryName = adapterSelection.categoryName
    rebuildAttributeMappings()
    draft.capabilities.forEach(ensureParamMappings)
  } else ElMessage.error(res.data?.message || '加载 Adapter 契约失败')
}

function normalizeAdapterContract(contract) {
  const c = contract && Object.keys(contract).length ? JSON.parse(JSON.stringify(contract)) : { config: { protocol: 'MQTT' }, commands: [], telemetry: {}, events: {} }
  c.config = c.config || { protocol: 'MQTT' }
  c.commands = asArray(c.commands).map(cmd => ({ commandName: cmd.commandName || cmd.name, description: cmd.description || '', commandParameters: asArray(cmd.commandParameters || cmd.parameters).map(p => ({ paramName: p.paramName || p.name, dataType: normalizeType(p.dataType), description: p.description || '', hidden: !!p.hidden, sourceField: p.sourceField })) }))
  c.telemetry = c.telemetry || {}
  c.telemetry.adapterAttributes = asArray(c.telemetry.adapterAttributes).map(a => ({ name: a.name, dataType: normalizeType(a.dataType), description: a.description || '' }))
  c.telemetry.attributesMapping = asArray(c.telemetry.attributesMapping)
  c.events = c.events || { cmdEvents: [], opEvents: [] }
  c.events.cmdEvents = asArray(c.events.cmdEvents).map(e => ({ eventName: e.eventName || e.name, description: e.description || '' }))
  c.events.opEvents = asArray(c.events.opEvents).map(e => ({ eventName: e.eventName || e.name, description: e.description || '' }))
  return c
}

function ensureTelemetryContract() {
  draft.adapterContract = normalizeAdapterContract(draft.adapterContract)
  return draft.adapterContract.telemetry
}

function rebuildAttributeMappings() {
  const telemetry = ensureTelemetryContract()
  telemetry.attributesMapping = asArray(telemetry.adapterAttributes).map(attr => {
    const compatible = draft.attributes.find(a => normalizeType(a.dataType) === normalizeType(attr.dataType) && !asArray(telemetry.attributesMapping).some(m => m.modelAttributeName === a.name))
    return { adapterAttrName: attr.name, modelAttributeName: compatible?.name || '' }
  })
}

function ensureParamMappings(cap) {
  const cmd = commandByName(cap.adapterCommandName)
  cap.parameterMapping = asArray(cmd?.commandParameters).filter(p => !p.hidden).map(param => {
    const existing = asArray(cap.parameterMapping).find(m => m.commandParamName === param.paramName)
    if (existing) return existing
    const compatible = cap.parameters.find(p => normalizeType(p.dataType) === normalizeType(param.dataType) && !asArray(cap.parameterMapping).some(m => m.capabilityParamName === p.name))
    return { _key: uid(), commandParamName: param.paramName, capabilityParamName: compatible?.name || '', isFixedValue: false, fixedValue: '' }
  })
}

function addAttribute() { draft.attributes.push({ _key: uid(), name: '', displayName: '', valueKind: 'CONTINUOUS', dataType: 'DOUBLE', unit: '' }); syncDefaultTemplateFromAttributes() }
function removeAttribute(idx) { draft.attributes.splice(idx, 1); syncDefaultTemplateFromAttributes() }
function addCapability() { draft.capabilities.push({ _key: uid(), name: '', displayName: '', adapterCommandName: '', parameters: [], parameterMapping: [] }) }
function removeCapability(idx) { draft.capabilities.splice(idx, 1) }
function addCapabilityParam(cap) { cap.parameters.push({ _key: uid(), name: '', displayName: '', dataType: 'DOUBLE' }) }
function addPort() { draft.ports.push({ _key: uid(), portName: '', direction: 'OUT', bindingAttrName: '' }) }
function addOpState() { draft.opState.states.push({ _key: uid(), stateName: 'STATE_' + (draft.opState.states.length + 1), onEntry: [] }) }
function removeOpState(state) { if (state.stateName === draft.opState.initialStateName) draft.opState.initialStateName = 'IDLE'; draft.opState.states = draft.opState.states.filter(s => s !== state) }
function addTransition() { draft.stateTransitions.push({ _key: uid(), description: '', fromStateName: draft.opState.initialStateName || 'IDLE', toStateName: '', trigger: { interfaceName: 'Interface_adapter_in', signalName: '' }, actions: [] }) }
function addBomSlot() { draft.componentsBom.push({ _key: uid(), componentName: '', categoryId: null, specificationText: '' }) }
function addParamMapping(cap) { cap.parameterMapping.push({ _key: uid(), commandParamName: '', capabilityParamName: '', isFixedValue: false, fixedValue: '' }) }
function removeParamMapping(cap, row) { cap.parameterMapping = asArray(cap.parameterMapping).filter(item => item !== row) }

function syncDefaultTemplateFromAttributes() {
  ensureIdentifiers()
  draft.defaultDataTemplate.main.templateName = draft.defaultDataTemplate.main.templateName || `${draft.modelName || '设备模型'} 默认数据模板`
  draft.defaultDataTemplate.main.templateDesc = draft.defaultDataTemplate.main.templateDesc || '系统根据设备模型自动生成的默认数据模板'
  draft.defaultDataTemplate.details = draft.attributes.flatMap(attr => {
    const name = attr.name || ''
    const desc = attr.displayName || name
    const rows = [{ columnName: name, columnDesc: desc, propertyTypeId: propertyTypeId(attr.dataType), columnLength: 255, deviceAttrKey: name, defaultValue: '' }]
    if (attr.unit) rows.push({ columnName: `${name}_unit`, columnDesc: `${desc}单位`, propertyTypeId: 6, columnLength: 50, deviceAttrKey: '', defaultValue: attr.unit })
    return rows
  })
}

function ensureIdentifiers() {
  draft.attributes.forEach((attr, idx) => { attr.name = attr.name || `attribute_${idx + 1}`; attr.displayName = attr.displayName || attr.name })
  draft.capabilities.forEach((cap, idx) => {
    cap.name = cap.name || `capability_${idx + 1}`
    cap.displayName = cap.displayName || cap.name
    cap.parameters.forEach((param, pIdx) => { param.name = param.name || `parameter_${pIdx + 1}`; param.displayName = param.displayName || param.name })
  })
}

function availableAttrsForMapping(row) { return draft.attributes.filter(attr => attr.name === row.modelAttributeName || !attributeMappings.value.some(m => m !== row && m.modelAttributeName === attr.name)) }
function availableCommandsForCapability(cap) { return adapterCommands.value.filter(cmd => cmd.commandName === cap.adapterCommandName || !draft.capabilities.some(other => other !== cap && other.adapterCommandName === cmd.commandName)) }
function availableCommandParams(cap, row) { const cmd = commandByName(cap.adapterCommandName); return asArray(cmd?.commandParameters).filter(p => !p.hidden && (p.paramName === row.commandParamName || !asArray(cap.parameterMapping).some(m => m !== row && m.commandParamName === p.paramName))) }
function availableCapabilityParams(cap, row) { return cap.parameters.filter(p => p.name === row.capabilityParamName || !asArray(cap.parameterMapping).some(m => m !== row && m.capabilityParamName === p.name)) }
function commandByName(name) { return adapterCommands.value.find(cmd => cmd.commandName === name) }
function attrDisplay(name) { return draft.attributes.find(attr => attr.name === name)?.displayName || '' }
function isUnitField(row) { return String(row.columnName || '').endsWith('_unit') }
function isLeafCategory(id) { return !childrenByCategory.value.has(String(id)) }
function categoryPath(id) { const parts = []; let cur = categories.value.find(c => String(c.id) === String(id)); while (cur) { parts.unshift(cur.categoryName); cur = categories.value.find(c => String(c.id) === String(cur.parentCategoryId)) } return parts.join(' / ') || '-' }
function modelIdOf(model) { return model?.modelId || model?.id }
function asArray(value) { return Array.isArray(value) ? value : [] }
function withKeys(rows) { return asArray(rows).map(row => ({ _key: uid(), ...row })) }
function normalizeType(type) { const t = String(type || 'STRING').toUpperCase(); return t === 'INT' ? 'INTEGER' : t === 'NUMBER' || t === 'FLOAT' ? 'DOUBLE' : t }
function propertyTypeId(type) { return normalizeType(type) === 'DOUBLE' ? 4 : normalizeType(type) === 'INTEGER' ? 3 : normalizeType(type) === 'BOOLEAN' ? 5 : 6 }
function uid() { return Math.random().toString(36).slice(2, 10) }
function errorMessage(err, fallback) { return err?.response?.data?.message || err?.message || fallback }
function isStandardTransition(t) { return ['EXECUTE_START', 'MANUAL_EXECUTE', 'COMMAND_RECEIVED', 'COMMAND_RUNNING', 'COMMAND_COMPLETED', 'COMMAND_FAILED', 'COMMAND_TIMEOUT', 'COMMAND_CANCELLED', 'EXECUTE_CANCEL', 'MANUAL_CANCEL', 'CONSTRAINT_CANCEL'].includes(t?.trigger?.signalName) }
function normalizeOpState(node) { const states = asArray(node?.states).map(s => ({ _key: uid(), stateName: s.stateName || String(s), onEntry: asArray(s.onEntry) })); return { initialStateName: node?.initialStateName || states[0]?.stateName || 'IDLE', states: states.length ? states : [{ _key: uid(), stateName: 'IDLE', onEntry: [] }] } }
function setSectionRef(key) { return el => { if (el) sectionRefs[key] = el } }
function scrollToSection(key) { sectionRefs[key]?.scrollIntoView({ behavior: 'smooth', block: 'start' }); activeSection.value = key }
function syncActiveSection() {
  if (scrollTicking) return
  scrollTicking = true
  requestAnimationFrame(() => {
    const top = contentRef.value?.scrollTop || 0
    const visible = navItems
      .map(item => [item.key, sectionRefs[item.key]?.offsetTop || 0])
      .filter(([, offset]) => offset <= top + 90)
      .pop()
    if (visible) activeSection.value = visible[0]
    scrollTicking = false
  })
}

onMounted(loadAll)
</script>

<style scoped>
.model-management-page { height: calc(100vh - 52px); display: flex; background: #eef2f6; color: #0f172a; }
.model-tree-pane { width: 390px; min-width: 390px; background: #f8fafc; border-right: 1px solid #cbd5e1; display: flex; flex-direction: column; }
.pane-title { min-height: 58px; padding: 10px 12px; display: flex; justify-content: space-between; align-items: center; border-bottom: 1px solid #dbe3ee; background: #fff; }
.pane-title strong { display: block; font-size: 15px; }
.pane-title span { color: #64748b; font-size: 12px; }
.tree-search { margin: 10px 12px; width: auto; }
.tree-editor { margin: 0 12px 10px; padding: 10px; border: 1px solid #bfdbfe; background: #eff6ff; display: grid; gap: 8px; }
.editor-caption { font-weight: 700; font-size: 13px; color: #1d4ed8; }
.editor-actions { display: flex; justify-content: flex-end; gap: 6px; }
.tree-scroll { flex: 1; min-height: 0; overflow: auto; padding: 4px 8px 12px; }
.tree-node { width: 100%; display: flex; align-items: center; justify-content: space-between; gap: 8px; padding: 2px 0; }
.tree-node.active .node-label { color: #1d4ed8; font-weight: 800; }
.tree-node.model .node-label { color: #0f766e; }
.node-main { min-width: 0; display: flex; align-items: center; gap: 6px; }
.node-label { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.node-actions { display: none; align-items: center; gap: 2px; }
.tree-node:hover .node-actions { display: flex; }
.node-actions .danger { color: #dc2626; }
.model-detail-pane { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.detail-toolbar { min-height: 70px; padding: 10px 16px; background: #fff; border-bottom: 1px solid #cbd5e1; display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.toolbar-title span { color: #64748b; font-size: 12px; }
.toolbar-title h1 { margin: 2px 0 0; font-size: 21px; line-height: 1.25; }
.toolbar-actions { display: flex; gap: 8px; flex-wrap: wrap; justify-content: flex-end; }
.empty-workspace { flex: 1; display: flex; align-items: center; justify-content: center; }
.detail-body { flex: 1; min-height: 0; display: flex; }
.section-nav { width: 164px; min-width: 164px; padding: 12px 10px; background: #f8fafc; border-right: 1px solid #dbe3ee; display: flex; flex-direction: column; gap: 6px; }
.section-nav button { height: 34px; border: 0; background: transparent; text-align: left; padding: 0 12px; border-left: 3px solid transparent; color: #475569; cursor: pointer; }
.section-nav button.active { background: #dbeafe; border-left-color: #2563eb; color: #1d4ed8; font-weight: 800; }
.section-scroll { flex: 1; min-width: 0; overflow: auto; padding: 10px 12px 22px; }
.editor-section { background: #fff; border: 1px solid #cbd5e1; border-left: 3px solid #2563eb; margin-bottom: 8px; padding: 10px 12px; }
.section-head { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; border-bottom: 1px solid #e2e8f0; padding-bottom: 8px; }
.section-head h2 { margin: 0; font-size: 16px; font-weight: 700; }
.section-head span { color: #64748b; font-size: 12px; }
.form-grid { display: grid; gap: 10px; }
.form-grid.two { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.form-grid.three { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.sub-section-title { min-height: 34px; display: flex; justify-content: space-between; align-items: center; margin: 2px 0 8px; }
.split-title { margin-top: 14px; }
.dense-table { border: 1px solid #dbe3ee; }
.table-head, .table-row { display: grid; align-items: center; gap: 8px; padding: 8px; border-bottom: 1px solid #e2e8f0; }
.table-head { background: #f1f5f9; color: #334155; font-weight: 800; }
.table-row:last-child { border-bottom: 0; }
.attr-grid { grid-template-columns: minmax(160px, 1fr) 130px 130px 120px 70px; }
.bom-grid { grid-template-columns: minmax(180px, 1fr) minmax(220px, 1fr) minmax(220px, 1.2fr) 70px; }
.capability-list, .capability-map-list, .transition-list { display: grid; gap: 8px; }
.capability-item, .map-card, .line-card, .transition-row { border: 1px solid #dbe3ee; background: #fff; padding: 8px; }
.capability-title, .map-card-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; }
.index-badge { width: 26px; height: 26px; border-radius: 4px; background: #e2e8f0; display: inline-flex; align-items: center; justify-content: center; font-weight: 800; color: #334155; }
.param-grid { border: 1px solid #dbe3ee; margin-bottom: 8px; }
.param-head, .param-row { display: grid; grid-template-columns: minmax(180px, 1fr) 150px 70px; gap: 8px; padding: 7px; border-bottom: 1px solid #e2e8f0; }
.param-head { background: #f1f5f9; font-weight: 800; }
.port-grid { display: grid; gap: 8px; }
.line-card { display: grid; grid-template-columns: 80px minmax(180px, 1fr) 60px 140px 80px minmax(180px, 1fr) 70px; align-items: center; gap: 8px; }
.contract-panels { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; }
.info-table { border: 1px solid #dbe3ee; background: #fff; }
.info-table-title { height: 38px; padding: 0 10px; display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid #dbe3ee; background: #f8fafc; }
.info-table table { width: 100%; border-collapse: collapse; font-size: 12px; }
.info-table th, .info-table td { border-bottom: 1px solid #e2e8f0; padding: 7px 8px; text-align: left; vertical-align: top; }
.mapping-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 8px; }
.mapping-row { border: 1px solid #dbe3ee; padding: 8px; display: grid; grid-template-columns: minmax(110px, 1fr) 24px minmax(110px, 1fr) minmax(190px, 1fr); gap: 8px; align-items: center; background: #f8fafc; }
.mapping-object { min-height: 32px; display: flex; align-items: center; padding: 0 8px; background: #ecfdf5; color: #047857; font-weight: 800; }
.mapping-object.adapter { background: #eff6ff; color: #1d4ed8; }
.arrow { text-align: center; color: #64748b; font-weight: 800; }
.param-map-row { display: grid; grid-template-columns: minmax(170px, 1fr) minmax(170px, 1fr) 120px minmax(150px, 1fr) 60px; gap: 8px; align-items: end; padding: 8px 0; border-top: 1px solid #e2e8f0; }
.field-block { display: grid; gap: 4px; }
.field-block span { font-size: 12px; color: #475569; font-weight: 700; }
.state-grid { display: flex; flex-wrap: wrap; gap: 8px; }
.state-pill { min-width: 260px; display: flex; align-items: center; gap: 8px; padding: 8px; background: #ecfdf5; border: 1px solid #bbf7d0; }
.state-pill.initial { border-color: #2563eb; background: #eff6ff; }
.transition-row { display: grid; grid-template-columns: minmax(140px, 1fr) 140px 24px 140px minmax(170px, 1fr) minmax(170px, 1fr) 60px; align-items: center; gap: 8px; }
.plain-table { width: 100%; }
.json-toolbar { margin-bottom: 8px; display: flex; justify-content: flex-end; }
.json-preview { margin: 0; max-height: 520px; overflow: auto; padding: 12px; background: #0f172a; color: #d1fae5; font-size: 12px; line-height: 1.55; }
@media (max-width: 1280px) { .contract-panels, .mapping-list { grid-template-columns: 1fr; } .form-grid.two, .form-grid.three { grid-template-columns: 1fr; } .line-card, .transition-row, .param-map-row { grid-template-columns: 1fr; } }
</style>

````

---

## Frontend/src/views/security/SecurityCenter.vue

````text
<template>
  <div class="security-page">
    <div class="page-heading">
      <div>
        <h1>约束管理</h1>
        <p>维护规则约束集合，查看系统执行中的违规记录。</p>
      </div>
      <div class="heading-actions">
        <el-button v-if="activeTab === 'rules'" :icon="Refresh" @click="fetchRules">刷新</el-button>
        <el-button v-if="activeTab === 'violations'" :icon="Refresh" @click="fetchViolations">刷新</el-button>
        <el-button v-if="activeTab === 'rules' && canCreateRule" type="primary" :icon="Plus" @click="openRuleDialog()">新增规则</el-button>
      </div>
    </div>

    <el-card shadow="never" class="page-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="约束规则" name="rules">
          <div class="toolbar">
            <el-input v-model="ruleQuery.keyword" clearable placeholder="搜索规则名称、对象、阈值或说明" :prefix-icon="Search" @keyup.enter="fetchRules" />
            <el-select v-model="ruleQuery.sourceType" clearable placeholder="来源类型">
              <el-option v-for="item in sourceTypes" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="ruleQuery.operator" clearable placeholder="比较符">
              <el-option v-for="item in operators" :key="item" :label="item" :value="item" />
            </el-select>
            <el-select v-model="ruleQuery.isEnabled" clearable placeholder="启用状态">
              <el-option label="启用" :value="true" />
              <el-option label="停用" :value="false" />
            </el-select>
            <el-button type="primary" plain :icon="Search" @click="fetchRules">查询</el-button>
          </div>

          <el-table :data="rules" border stripe size="small" v-loading="loadingRules" class="data-table">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="ruleName" label="约束名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="sourceType" label="来源类型" min-width="190" show-overflow-tooltip />
            <el-table-column prop="objectEndpoint" label="监控端点" min-width="150" show-overflow-tooltip />
            <el-table-column prop="objectName" label="约束对象" min-width="150" show-overflow-tooltip />
            <el-table-column label="条件" min-width="140">
              <template #default="{ row }">
                <span class="condition-text">{{ row.operator }} {{ row.threshold }}</span>
              </template>
            </el-table-column>
            <el-table-column label="违规动作" min-width="220">
              <template #default="{ row }">
                <div class="action-tags">
                  <el-tag v-for="(action, index) in normalizeActions(row.violationActions)" :key="index" size="small" :type="action.actionType === 'SYSTEM' ? 'warning' : 'info'">
                    {{ summarizeAction(action) }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="92">
              <template #default="{ row }">
                <el-switch
                  v-model="row.isEnabled"
                  :disabled="!canEditRule"
                  size="small"
                  @change="(value) => toggleRule(row, value)"
                />
              </template>
            </el-table-column>
            <el-table-column prop="description" label="说明" min-width="180" show-overflow-tooltip />
            <el-table-column label="创建时间" min-width="155">
              <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="150" fixed="right">
              <template #default="{ row }">
                <el-button v-if="canEditRule" link type="primary" @click="openRuleDialog(row)">编辑</el-button>
                <el-popconfirm v-if="canDeleteRule" title="确认删除该约束规则？" @confirm="deleteRule(row)">
                  <template #reference>
                    <el-button link type="danger">删除</el-button>
                  </template>
                </el-popconfirm>
                <span v-if="!canEditRule && !canDeleteRule" class="muted">无操作权限</span>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              v-model:current-page="ruleQuery.pageNo"
              v-model:page-size="ruleQuery.pageSize"
              :total="ruleTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchRules"
              @size-change="fetchRules"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="违规日志" name="violations">
          <div class="toolbar">
            <el-input v-model="violationQuery.keyword" clearable placeholder="搜索观测变量、动作或约束类型" :prefix-icon="Search" @keyup.enter="fetchViolations" />
            <el-input v-model="violationQuery.constraintRuleId" clearable placeholder="规则 ID" />
            <el-input v-model="violationQuery.taskId" clearable placeholder="任务 ID" />
            <el-input v-model="violationQuery.deviceInstanceId" clearable placeholder="设备实例 ID" />
            <el-button type="primary" plain :icon="Search" @click="fetchViolations">查询</el-button>
            <el-button v-if="canExportViolation" :icon="Download" @click="exportViolations">导出当前页</el-button>
          </div>

          <el-table :data="violations" border stripe size="small" v-loading="loadingViolations" class="data-table">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="constraintRuleId" label="规则 ID" width="90" />
            <el-table-column prop="constraintType" label="约束类型" min-width="130" show-overflow-tooltip />
            <el-table-column prop="taskId" label="任务 ID" width="90" />
            <el-table-column prop="taskStepId" label="步骤 ID" width="90" />
            <el-table-column prop="deviceInstanceId" label="设备实例" width="105" />
            <el-table-column prop="observedVariable" label="观测变量" min-width="150" show-overflow-tooltip />
            <el-table-column label="期望条件" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ compactJson(row.expectedCondition) }}</template>
            </el-table-column>
            <el-table-column label="实际值" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">{{ compactJson(row.actualValue) }}</template>
            </el-table-column>
            <el-table-column prop="actionTaken" label="执行动作" min-width="160" show-overflow-tooltip />
            <el-table-column label="发生时间" min-width="155">
              <template #default="{ row }">{{ formatTime(row.violationTime) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="90" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openViolationDrawer(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              v-model:current-page="violationQuery.pageNo"
              v-model:page-size="violationQuery.pageSize"
              :total="violationTotal"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="fetchViolations"
              @size-change="fetchViolations"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <el-dialog v-model="ruleDialogVisible" :title="ruleDialogMode === 'create' ? '新增约束规则' : '编辑约束规则'" width="820px" destroy-on-close>
      <el-form :model="ruleForm" :rules="ruleFormRules" ref="ruleFormRef" label-width="108px" size="small">
        <div class="form-grid">
          <el-form-item label="约束名称" prop="ruleName">
            <el-input v-model="ruleForm.ruleName" placeholder="例如：反应釜温度上限" />
          </el-form-item>
          <el-form-item label="来源类型" prop="sourceType">
            <el-select v-model="ruleForm.sourceType" style="width: 100%" placeholder="请选择来源类型" @change="onSourceTypeChange">
              <el-option v-for="item in sourceTypes" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="监控端点" prop="objectEndpoint">
            <el-input v-model="ruleForm.objectEndpoint" placeholder="设备实例 ID / 节点定位" />
          </el-form-item>
          <el-form-item label="约束对象" prop="objectName">
            <el-input v-model="ruleForm.objectName" placeholder="用户定义的 observable object name" />
          </el-form-item>
          <el-form-item label="比较符" prop="operator">
            <el-select v-model="ruleForm.operator" style="width: 100%">
              <el-option v-for="item in operators" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
          <el-form-item label="阈值" prop="threshold">
            <el-input v-model="ruleForm.threshold" placeholder="如 80 或 [60,90]" />
          </el-form-item>
        </div>
        <el-form-item label="规则说明">
          <el-input v-model="ruleForm.description" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="启用规则">
          <el-switch v-model="ruleForm.isEnabled" />
        </el-form-item>

        <div class="subsection">
          <div class="subsection-header">
            <span>违规触发动作集</span>
            <div>
              <el-button size="small" :icon="Plus" @click="addSystemAction">系统动作</el-button>
              <el-button size="small" :icon="Plus" @click="addDeviceAction">设备能力</el-button>
            </div>
          </div>
          <el-table :data="ruleForm.violationActions" border size="small" empty-text="请至少添加一个违规动作">
            <el-table-column label="类型" width="150">
              <template #default="{ row }">
                <el-select v-model="row.actionType" @change="onActionTypeChange(row)">
                  <el-option label="SYSTEM" value="SYSTEM" />
                  <el-option label="DEVICE_CAPABILITY" value="DEVICE_CAPABILITY" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="动作内容" min-width="420">
              <template #default="{ row }">
                <div v-if="row.actionType === 'SYSTEM'" class="inline-controls">
                  <el-select v-model="row.action" placeholder="系统动作" style="width: 180px">
                    <el-option v-for="item in systemActions" :key="item" :label="item" :value="item" />
                  </el-select>
                </div>
                <div v-else class="device-action-editor">
                  <div class="inline-controls">
                    <el-select v-model="row.deviceInstanceId" filterable placeholder="设备实例" style="width: 210px" @change="() => onDeviceActionInstanceChange(row)">
                      <el-option v-for="item in deviceInstances" :key="item.id" :label="deviceInstanceLabel(item)" :value="Number(item.id || item.instanceId)" />
                    </el-select>
                    <el-select v-model="row.capabilityName" filterable placeholder="设备能力" style="width: 190px">
                      <el-option v-for="cap in capabilityOptions(row)" :key="cap.name" :label="cap.displayName || cap.name" :value="cap.name" />
                    </el-select>
                  </div>
                  <el-input v-model="row.parametersText" type="textarea" :rows="2" class="json-input" placeholder='参数 JSON，例如 {"targetTemperature":80}' />
                </div>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="80">
              <template #default="{ $index }">
                <el-button link type="danger" @click="removeAction($index)">移除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="ruleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingRule" @click="submitRule">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="violationDrawerVisible" title="违规日志详情" size="560px">
      <el-descriptions v-if="currentViolation" :column="1" border size="small">
        <el-descriptions-item label="日志 ID">{{ currentViolation.id }}</el-descriptions-item>
        <el-descriptions-item label="规则 ID">{{ currentViolation.constraintRuleId }}</el-descriptions-item>
        <el-descriptions-item label="约束类型">{{ currentViolation.constraintType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务/步骤">{{ currentViolation.taskId || '-' }} / {{ currentViolation.taskStepId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="设备实例">{{ currentViolation.deviceInstanceId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="观测变量">{{ currentViolation.observedVariable || '-' }}</el-descriptions-item>
        <el-descriptions-item label="执行动作">{{ currentViolation.actionTaken || '-' }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{ formatTime(currentViolation.violationTime) }}</el-descriptions-item>
      </el-descriptions>
      <div class="json-detail">
        <h3>期望条件</h3>
        <pre>{{ prettyJson(currentViolation?.expectedCondition) }}</pre>
        <h3>实际值</h3>
        <pre>{{ prettyJson(currentViolation?.actualValue) }}</pre>
        <h3>变量快照</h3>
        <pre>{{ prettyJson(currentViolation?.variableSnapshot) }}</pre>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import axios from 'axios'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { Download, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { useAuthStore } from '../../stores/authStore'

const authStore = useAuthStore()
const activeTab = ref('rules')

const canCreateRule = computed(() => authStore.hasPermission('constraint_rule:create'))
const canEditRule = computed(() => authStore.hasPermission('constraint_rule:edit'))
const canDeleteRule = computed(() => authStore.hasPermission('constraint_rule:delete'))
const canViewViolation = computed(() => authStore.hasPermission('violation_log:view'))
const canExportViolation = computed(() => authStore.hasPermission('violation_log:export'))

const sourceTypes = ref<string[]>([])
const operators = ref<string[]>([])
const systemActions = ref<string[]>([])
const deviceModels = ref<any[]>([])
const deviceInstances = ref<any[]>([])

const loadingRules = ref(false)
const rules = ref<any[]>([])
const ruleTotal = ref(0)
const ruleQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  sourceType: '',
  objectEndpoint: '',
  objectName: '',
  operator: '',
  isEnabled: undefined as boolean | undefined
})

const loadingViolations = ref(false)
const violations = ref<any[]>([])
const violationTotal = ref(0)
const violationQuery = reactive({
  pageNo: 1,
  pageSize: 20,
  keyword: '',
  constraintRuleId: '',
  taskId: '',
  deviceInstanceId: ''
})

const ruleDialogVisible = ref(false)
const ruleDialogMode = ref<'create' | 'edit'>('create')
const ruleFormRef = ref<FormInstance>()
const savingRule = ref(false)
const ruleForm = ref<any>(emptyRuleForm())

const violationDrawerVisible = ref(false)
const currentViolation = ref<any>(null)

const ruleFormRules: FormRules = {
  ruleName: [{ required: true, message: '请输入约束名称', trigger: 'blur' }],
  sourceType: [{ required: true, message: '请选择来源类型', trigger: 'change' }],
  objectEndpoint: [{ required: true, message: '请输入监控端点', trigger: 'blur' }],
  objectName: [{ required: true, message: '请输入约束对象名称', trigger: 'blur' }],
  operator: [{ required: true, message: '请选择比较符', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入阈值界限', trigger: 'blur' }]
}

function emptyRuleForm() {
  return {
    id: null,
    ruleName: '',
    sourceType: '',
    objectEndpoint: '',
    objectName: '',
    operator: 'GT',
    threshold: '',
    violationActions: [] as any[],
    description: '',
    isEnabled: true
  }
}

async function fetchOptions() {
  try {
    const res = await axios.get('/api/constraint/rule/options')
    if (res.data?.success) {
      sourceTypes.value = res.data.data?.sourceTypes || []
      operators.value = res.data.data?.operators || []
      systemActions.value = res.data.data?.systemViolationActions || []
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载约束选项失败')
  }
}

async function fetchDeviceRefs() {
  try {
    const [modelRes, instanceRes] = await Promise.all([
      axios.get('/api/device/model/list'),
      axios.get('/api/device/instance/list')
    ])
    if (modelRes.data?.success) deviceModels.value = modelRes.data.data || []
    if (instanceRes.data?.success) deviceInstances.value = instanceRes.data.data || []
  } catch {
    // Device refs are optional for rule table rendering.
  }
}

async function fetchRules() {
  loadingRules.value = true
  try {
    const res = await axios.get('/api/constraint/rule/page', { params: cleanParams(ruleQuery) })
    if (res.data?.success) {
      const data = res.data.data || {}
      rules.value = data.records || []
      ruleTotal.value = Number(data.total || 0)
    } else {
      ElMessage.error(res.data?.message || '加载约束规则失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载约束规则失败')
  } finally {
    loadingRules.value = false
  }
}

async function fetchViolations() {
  if (!canViewViolation.value) return
  loadingViolations.value = true
  try {
    const res = await axios.get('/api/constraint/violation/page', { params: cleanParams(violationQuery) })
    if (res.data?.success) {
      const data = res.data.data || {}
      violations.value = data.records || []
      violationTotal.value = Number(data.total || 0)
    } else {
      ElMessage.error(res.data?.message || '加载违规日志失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载违规日志失败')
  } finally {
    loadingViolations.value = false
  }
}

function openRuleDialog(row?: any) {
  ruleDialogMode.value = row?.id ? 'edit' : 'create'
  ruleForm.value = row ? toEditableRule(row) : emptyRuleForm()
  ruleDialogVisible.value = true
}

function toEditableRule(row: any) {
  return {
    id: row.id,
    ruleName: row.ruleName || '',
    sourceType: row.sourceType || '',
    objectEndpoint: row.objectEndpoint || '',
    objectName: row.objectName || '',
    operator: row.operator || 'GT',
    threshold: row.threshold || '',
    description: row.description || '',
    isEnabled: row.isEnabled !== false,
    violationActions: normalizeActions(row.violationActions).map((action: any) => ({
      ...action,
      parametersText: action.parameters ? JSON.stringify(action.parameters) : '{}'
    }))
  }
}

async function submitRule() {
  if (!ruleFormRef.value) return
  await ruleFormRef.value.validate(async (valid) => {
    if (!valid) return
    const payload = buildRulePayload()
    if (!payload) return
    savingRule.value = true
    try {
      const request = payload.id
        ? axios.put(`/api/constraint/rule/${payload.id}`, payload)
        : axios.post('/api/constraint/rule', payload)
      const res = await request
      if (res.data?.success) {
        ElMessage.success('保存成功')
        ruleDialogVisible.value = false
        await fetchRules()
      } else {
        ElMessage.error(res.data?.message || '保存失败')
      }
    } catch (err: any) {
      ElMessage.error(err?.response?.data?.message || '保存失败')
    } finally {
      savingRule.value = false
    }
  })
}

function buildRulePayload() {
  if (!ruleForm.value.violationActions.length) {
    ElMessage.warning('请至少添加一个违规触发动作')
    return null
  }
  const actions: any[] = []
  for (const [index, action] of ruleForm.value.violationActions.entries()) {
    if (action.actionType === 'SYSTEM') {
      if (!action.action) {
        ElMessage.warning(`第 ${index + 1} 个系统动作未选择 action`)
        return null
      }
      actions.push({ actionType: 'SYSTEM', action: action.action })
    } else if (action.actionType === 'DEVICE_CAPABILITY') {
      if (!action.deviceInstanceId || !action.capabilityName) {
        ElMessage.warning(`第 ${index + 1} 个设备能力动作缺少设备实例或能力名`)
        return null
      }
      const parsed = parseJsonObject(action.parametersText || '{}', `第 ${index + 1} 个设备能力动作参数`)
      if (parsed === null) return null
      actions.push({
        actionType: 'DEVICE_CAPABILITY',
        deviceInstanceId: Number(action.deviceInstanceId),
        capabilityName: action.capabilityName,
        parameters: parsed
      })
    }
  }
  return {
    id: ruleForm.value.id,
    ruleName: ruleForm.value.ruleName,
    sourceType: ruleForm.value.sourceType,
    objectEndpoint: ruleForm.value.objectEndpoint,
    objectName: ruleForm.value.objectName,
    operator: ruleForm.value.operator,
    threshold: ruleForm.value.threshold,
    violationActions: actions,
    description: ruleForm.value.description,
    isEnabled: ruleForm.value.isEnabled
  }
}

async function deleteRule(row: any) {
  try {
    const res = await axios.delete(`/api/constraint/rule/${row.id}`)
    if (res.data?.success) {
      ElMessage.success('删除成功')
      await fetchRules()
    } else {
      ElMessage.error(res.data?.message || '删除失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '删除失败')
  }
}

async function toggleRule(row: any, enabled: boolean) {
  try {
    const res = await axios.patch(`/api/constraint/rule/${row.id}/enabled`, null, { params: { enabled } })
    if (!res.data?.success) throw new Error(res.data?.message || '更新状态失败')
    ElMessage.success(enabled ? '规则已启用' : '规则已停用')
  } catch (err: any) {
    row.isEnabled = !enabled
    ElMessage.error(err?.response?.data?.message || err?.message || '更新状态失败')
  }
}

function addSystemAction() {
  ruleForm.value.violationActions.push({ actionType: 'SYSTEM', action: systemActions.value[0] || 'ALERT' })
}

function addDeviceAction() {
  ruleForm.value.violationActions.push({ actionType: 'DEVICE_CAPABILITY', deviceInstanceId: undefined, capabilityName: '', parametersText: '{}' })
}

function removeAction(index: number) {
  ruleForm.value.violationActions.splice(index, 1)
}

function onActionTypeChange(row: any) {
  if (row.actionType === 'SYSTEM') {
    row.action = row.action || systemActions.value[0] || 'ALERT'
    delete row.deviceInstanceId
    delete row.capabilityName
    delete row.parametersText
  } else {
    row.deviceInstanceId = undefined
    row.capabilityName = ''
    row.parametersText = '{}'
    delete row.action
  }
}

function onDeviceActionInstanceChange(row: any) {
  row.capabilityName = ''
}

function onSourceTypeChange() {
  if (!ruleForm.value.objectName && ruleForm.value.sourceType) {
    ruleForm.value.objectName = ruleForm.value.sourceType
  }
}

function capabilityOptions(action: any) {
  const instance = deviceInstances.value.find((item: any) => Number(item.id || item.instanceId) === Number(action.deviceInstanceId))
  const modelId = instance?.deviceModelId || instance?.modelId
  const model = deviceModels.value.find((item: any) => Number(item.id || item.modelId) === Number(modelId))
  return Array.isArray(model?.capabilities) ? model.capabilities : []
}

function deviceInstanceLabel(item: any) {
  return `${item.instanceName || item.name || '设备实例'} #${item.id || item.instanceId}`
}

function normalizeActions(value: any) {
  if (Array.isArray(value)) return value
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

function summarizeAction(action: any) {
  if (action.actionType === 'SYSTEM') return `SYSTEM:${action.action || '-'}`
  if (action.actionType === 'DEVICE_CAPABILITY') return `DEVICE:${action.capabilityName || '-'}@${action.deviceInstanceId || '-'}`
  return action.actionType || '-'
}

function parseJsonObject(text: string, label: string) {
  try {
    const parsed = text?.trim() ? JSON.parse(text) : {}
    if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
      ElMessage.warning(`${label}必须是 JSON 对象`)
      return null
    }
    return parsed
  } catch {
    ElMessage.warning(`${label}不是合法 JSON`)
    return null
  }
}

function openViolationDrawer(row: any) {
  currentViolation.value = row
  violationDrawerVisible.value = true
}

function exportViolations() {
  const rows = violations.value.map(row => JSON.stringify(row)).join('\n')
  const blob = new Blob([rows], { type: 'application/json;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `violation-log-page-${violationQuery.pageNo}.jsonl`
  link.click()
  URL.revokeObjectURL(url)
}

function onTabChange(name: string | number) {
  if (name === 'rules') fetchRules()
  if (name === 'violations') fetchViolations()
}

function cleanParams(source: Record<string, any>) {
  const params: Record<string, any> = {}
  Object.entries(source).forEach(([key, value]) => {
    if (value !== '' && value !== undefined && value !== null) params[key] = value
  })
  return params
}

function compactJson(value: any) {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'string') return value
  return JSON.stringify(value)
}

function prettyJson(value: any) {
  if (value === null || value === undefined) return '-'
  if (typeof value === 'string') {
    try { return JSON.stringify(JSON.parse(value), null, 2) } catch { return value }
  }
  return JSON.stringify(value, null, 2)
}

function formatTime(time?: string) {
  return time ? new Date(time).toLocaleString('zh-CN', { hour12: false }) : '-'
}

onMounted(async () => {
  await Promise.all([fetchOptions(), fetchDeviceRefs()])
  await fetchRules()
})
</script>

<style scoped>
.security-page {
  min-height: calc(100vh - 52px);
  background: #f3f5f8;
  padding: 16px;
  overflow: auto;
}

.page-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.page-heading h1 {
  margin: 0;
  font-size: 20px;
  font-weight: 650;
  color: #1f2937;
}

.page-heading p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}

.heading-actions,
.toolbar,
.inline-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-card {
  border: 1px solid #d9dee7;
  border-radius: 6px;
}

.toolbar {
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.toolbar .el-input {
  width: 260px;
}

.toolbar .el-select {
  width: 180px;
}

.data-table {
  width: 100%;
}

.condition-text {
  font-family: Consolas, Menlo, monospace;
  color: #334155;
}

.action-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.pagination-row {
  display: flex;
  justify-content: flex-end;
  padding-top: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0 12px;
}

.subsection {
  border: 1px solid #dfe4ec;
  border-radius: 6px;
  padding: 12px;
  background: #fbfcfe;
}

.subsection-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
  font-weight: 600;
  color: #334155;
}

.device-action-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.json-input :deep(textarea),
.json-detail pre {
  font-family: Consolas, Menlo, monospace;
  font-size: 12px;
  line-height: 1.45;
}

.json-detail {
  margin-top: 14px;
}

.json-detail h3 {
  margin: 14px 0 6px;
  font-size: 13px;
  color: #334155;
}

.json-detail pre {
  margin: 0;
  padding: 10px;
  background: #0f172a;
  color: #e2e8f0;
  border-radius: 6px;
  white-space: pre-wrap;
  word-break: break-word;
}

.muted {
  color: #94a3b8;
  font-size: 12px;
}

@media (max-width: 900px) {
  .page-heading {
    align-items: flex-start;
    flex-direction: column;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .toolbar .el-input,
  .toolbar .el-select {
    width: 100%;
  }
}
</style>
````

---

## Frontend/src/views/task/TaskList.vue

````text
<template>
  <div class="task-list-fullscreen">
    <!-- Main Table Card -->
    <el-card class="table-card-fullscreen" shadow="never">
      <template #header>
        <div class="card-header-fullscreen">
          <span class="header-title">任务列表</span>
          <div class="header-actions-right">
            <el-button @click="refreshTaskList" :loading="loading">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-button type="primary" class="premium-btn" @click="openCreateDrawer">
              <el-icon class="mr-1"><Plus /></el-icon> 新建任务
            </el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="tasks"
        v-loading="loading"
        style="width: 100%; height: 100%;"
        height="100%"
        border
        stripe
        highlight-current-row
        @row-click="handleRowClick"
        class="custom-table"
      >
        <el-table-column prop="taskId" label="ID" width="72" align="center" />
        <el-table-column prop="taskName" label="任务名称" min-width="160">
          <template #default="{ row }">
            <div style="font-weight: 600; color: #0f172a;">{{ row.taskName }}</div>
            <div v-if="row.taskDesc" style="font-size: 12px; color: #64748b; margin-top: 2px;">{{ row.taskDesc }}</div>
          </template>
        </el-table-column>
        <el-table-column label="关联流程" min-width="160">
          <template #default="{ row }">
            <div v-if="getWorkflowName(row.templateId) !== row.templateId" style="font-weight: 500; color: #334155;">{{ getWorkflowName(row.templateId) }}</div>
            <div style="font-size: 11px; color: #94a3b8; font-family: monospace;">ID: {{ row.templateId || '-' }}</div>
          </template>
        </el-table-column>
        <el-table-column prop="currentStatus" label="状态" width="110" align="center">
          <template #default="{ row }">
            <div class="status-badge-container">
              <span :class="['pulse-dot', (row.currentStatus || '').toLowerCase()]" v-if="row.currentStatus === 'RUNNING'"></span>
              <el-tag :type="getStatusType(row.currentStatus)" effect="dark" class="status-tag">
                {{ getStatusLabel(row.currentStatus) }}
              </el-tag>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="当前节点" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.currentNodeIdRef != null" type="info" size="small" effect="plain">节点 #{{ row.currentNodeIdRef }}</el-tag>
            <span v-else style="color: #94a3b8; font-size: 12px;">-</span>
          </template>
        </el-table-column>
        <el-table-column label="执行周期" min-width="200">
          <template #default="{ row }">
            <div class="time-range-cell">
              <div><span class="time-label">始：</span>{{ formatTime(row.startTime) }}</div>
              <div v-if="row.endTime"><span class="time-label">终：</span>{{ formatTime(row.endTime) }}</div>
              <div v-else-if="row.currentStatus === 'RUNNING'" style="color: #2563eb; font-size: 12px;">运行中...</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="资源绑定" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.resourceMap && Object.keys(row.resourceMap || {}).length > 0"
              type="success" size="small" effect="plain">
              {{ Object.keys(row.resourceMap).length }} 个设备
            </el-tag>
            <span v-else style="color: #94a3b8; font-size: 12px;">无</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <div class="action-buttons" @click.stop>
              <el-button
                type="primary"
                size="small"
                plain
                :disabled="row.currentStatus !== 'PENDING'"
                @click="startTask(row.taskId)"
              >
                启动
              </el-button>
              <el-button
                type="warning"
                size="small"
                plain
                :disabled="!['PENDING', 'RUNNING'].includes(row.currentStatus)"
                @click="abortTask(row.taskId)"
              >
                终止
              </el-button>
              <el-button
                type="danger"
                size="small"
                plain
                @click="confirmDeleteTask(row.taskId)"
              >
                删除
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="taskPageNo"
          v-model:page-size="taskPageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="taskTotal"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleTaskPageSizeChange"
          @current-change="handleTaskPageChange"
        />
      </div>
    </el-card>

    <!-- Create Task Drawer -->
    <el-drawer v-model="createDrawerVisible" title="新建任务" size="640px" class="model-drawer" destroy-on-close>
      <div class="drawer-body" style="padding: 0 16px;">
        <el-form
          ref="createFormRef"
          :model="createForm"
          :rules="createRules"
          label-width="120px"
          label-position="top"
          class="basic-form"
          style="max-width: 100%;"
        >
          <div class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px;">基础配置</h2>
            <section class="drawer-section">
              <el-form-item label="任务名称" prop="taskName">
                <el-input v-model="createForm.taskName" placeholder="例如：批次PCR扩增与物料搅拌" />
              </el-form-item>
              <el-form-item label="关联流程" prop="templateId">
                <el-select
                  v-model="createForm.templateId"
                  placeholder="请选择流程"
                  style="width: 100%"
                  v-loading="loadingWorkflows"
                  @change="handleTemplateChange"
                >
                  <el-option
                    v-for="tpl in processTemplates"
                    :key="tpl.templateId"
                    :label="tpl.templateName"
                    :value="tpl.templateId"
                  />
                </el-select>
              </el-form-item>
            </section>
          </div>

          <div class="anchor-section industrial-section" v-if="requiredModels.length > 0">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px; margin-top: 24px;">设备资源绑定</h2>
            <section class="drawer-section">
              <div class="schema-info-bar" style="margin-bottom: 12px; padding: 8px; background: #f8fafc; border-radius: 4px; font-size: 12px; color: #64748b;">
                <span>请为以下所需设备分配真实的物理实例：</span>
              </div>
              <div v-for="model in requiredModels" :key="model.nodeId" style="width: 100%; margin-bottom: 16px;">
                <div style="font-size: 13px; margin-bottom: 6px; font-weight: 600; color: #334155;">{{ model.nodeType }} (节点: {{ model.nodeIdRef }}):</div>
                <el-select v-model="createForm.resourceMap[model.nodeIdRef]" placeholder="请选择设备实例" style="width: 100%">
                  <el-option
                    v-for="inst in availableInstances[model.deviceModelId] || []"
                    :key="inst.id"
                    :label="inst.deviceName || inst.id"
                    :value="inst.id"
                  />
                </el-select>
              </div>
            </section>
          </div>

          <div class="anchor-section industrial-section">
            <h2 style="margin-bottom: 16px; border-left: 4px solid var(--el-color-primary); padding-left: 12px; margin-top: 24px;">任务补充约束</h2>
            
            <section class="drawer-section">
              <div class="section-title">
                <h3 style="font-size: 14px;">可观测对象 (Observable Objects)</h3>
              </div>
              <el-table :data="createForm.constraintsForm.observableObjects" size="small" border>
                <el-table-column prop="name" label="对象名称(标识)" width="150">
                  <template #default="{ row }">
                    <el-input v-model="row.name" placeholder="唯一名称" size="small" />
                  </template>
                </el-table-column>
                <el-table-column prop="sourceType" label="来源类型" width="180">
                  <template #default="{ row }">
                    <el-select v-model="row.sourceType" size="small">
                      <el-option label="设备属性" value="DEVICE_ATTRIBUTE" />
                      <el-option label="设备操作状态" value="DEVICE_OPERATION_STATE" />
                      <el-option label="设备指令生命周期" value="DEVICE_COMMAND_LIFECYCLE" />
                      <el-option label="节点生命周期状态" value="NODE_LIFECYCLE_STATE" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column prop="dataType" label="数据类型" width="120">
                  <template #default="{ row }">
                    <el-select v-model="row.dataType" size="small">
                      <el-option label="FLOAT" value="FLOAT" />
                      <el-option label="INT" value="INT" />
                      <el-option label="BOOL" value="BOOL" />
                      <el-option label="STRING" value="STRING" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="详情配置" min-width="300">
                  <template #default="{ row }">
                    <div v-if="row.sourceType === 'DEVICE_ATTRIBUTE'" style="display: flex; gap: 8px;">
                      <el-select v-model="row.deviceModelId" placeholder="选择模型" size="small" @change="fetchModelAttributes(row)" style="width: 120px;">
                        <el-option v-for="m in allModels" :key="m.id" :label="m.modelName" :value="m.id" />
                      </el-select>
                      <el-select v-model="row.deviceInstanceId" placeholder="选择实例(可选)" size="small" clearable style="width: 120px;">
                        <el-option v-for="inst in availableInstances[row.deviceModelId] || []" :key="inst.id" :label="inst.deviceName || inst.id" :value="inst.id" />
                      </el-select>
                      <el-select v-model="row.targetName" placeholder="属性名" size="small" style="width: 120px;">
                        <el-option v-for="attr in modelAttributes[row.deviceModelId] || []" :key="attr" :label="attr" :value="attr" />
                      </el-select>
                    </div>
                    <div v-else-if="row.sourceType === 'DEVICE_OPERATION_STATE' || row.sourceType === 'DEVICE_COMMAND_LIFECYCLE'" style="display: flex; gap: 8px;">
                      <el-select v-model="row.deviceModelId" placeholder="选择模型" size="small" @change="fetchModelAttributes(row)" style="width: 120px;">
                        <el-option v-for="m in allModels" :key="m.id" :label="m.modelName" :value="m.id" />
                      </el-select>
                      <el-select v-model="row.deviceInstanceId" placeholder="选择实例(可选)" size="small" clearable style="width: 120px;">
                        <el-option v-for="inst in availableInstances[row.deviceModelId] || []" :key="inst.id" :label="inst.deviceName || inst.id" :value="inst.id" />
                      </el-select>
                    </div>
                    <div v-else-if="row.sourceType === 'NODE_LIFECYCLE_STATE'" style="display: flex; gap: 8px;">
                      <el-select v-model="row.workflowTemplateId" placeholder="流程模板(可选)" size="small" clearable style="width: 150px;">
                        <el-option v-for="tpl in processTemplates" :key="tpl.templateId" :label="tpl.templateName" :value="tpl.templateId" />
                      </el-select>
                      <el-input v-model="row.nodeName" placeholder="节点名(nodeIdRef)" size="small" style="width: 120px;" />
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="60" align="center">
                  <template #default="{ $index }">
                    <el-button type="danger" :icon="Delete" circle plain size="small" @click="createForm.constraintsForm.observableObjects.splice($index, 1)" />
                  </template>
                </el-table-column>
              </el-table>
              <el-button type="primary" plain size="small" style="margin-top: 8px;" @click="addObservableObject">+ 添加可观测对象</el-button>
            </section>

            <section class="drawer-section">
              <div class="section-title">
                <h3 style="font-size: 14px;">约束规则 (Constraints)</h3>
              </div>
              <el-table :data="createForm.constraintsForm.constraints" size="small" border>
                <el-table-column prop="name" label="规则名称" width="120">
                  <template #default="{ row }">
                    <el-input v-model="row.name" placeholder="规则名" size="small" />
                  </template>
                </el-table-column>
                <el-table-column prop="observedObjectName" label="观测对象" width="150">
                  <template #default="{ row }">
                    <el-select v-model="row.observedObjectName" size="small" placeholder="请选择">
                      <el-option v-for="obj in createForm.constraintsForm.observableObjects" :key="obj.name" :label="obj.name" :value="obj.name" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column prop="operator" label="运算符" width="120">
                  <template #default="{ row }">
                    <el-select v-model="row.operator" size="small">
                      <el-option v-for="op in ['GT','LT','EQ','NEQ','GTE','LTE','IN','NOT_IN']" :key="op" :label="op" :value="op" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column prop="threshold" label="阈值" width="120">
                  <template #default="{ row }">
                    <el-input v-model="row.threshold" placeholder="阈值" size="small" />
                  </template>
                </el-table-column>
                <el-table-column label="违规动作" min-width="250">
                  <template #default="{ row }">
                    <div v-for="(act, aIndex) in row.violationActions" :key="aIndex" style="display: flex; gap: 4px; margin-bottom: 4px;">
                      <el-select v-model="act.actionType" size="small" style="width: 100px;">
                        <el-option label="系统动作" value="SYSTEM" />
                        <el-option label="设备动作" value="DEVICE_CAPABILITY" />
                      </el-select>
                      <template v-if="act.actionType === 'SYSTEM'">
                        <el-select v-model="act.action" size="small" style="flex: 1;">
                          <el-option label="终止任务" value="ABORT_TASK" />
                          <el-option label="告警" value="ALERT" />
                        </el-select>
                      </template>
                      <template v-else-if="act.actionType === 'DEVICE_CAPABILITY'">
                         <el-input v-model="act.deviceInstanceId" placeholder="实例ID" size="small" style="width: 80px;" />
                         <el-input v-model="act.capabilityName" placeholder="指令名" size="small" style="flex: 1;" />
                      </template>
                      <el-button type="danger" icon="Delete" circle plain size="small" @click="row.violationActions.splice(aIndex, 1)" />
                    </div>
                    <el-button type="primary" plain size="small" @click="row.violationActions.push({ actionType: 'SYSTEM', action: 'ALERT' })">+ 添加动作</el-button>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="60" align="center">
                  <template #default="{ $index }">
                    <el-button type="danger" :icon="Delete" circle plain size="small" @click="createForm.constraintsForm.constraints.splice($index, 1)" />
                  </template>
                </el-table-column>
              </el-table>
              <el-button type="primary" plain size="small" style="margin-top: 8px;" @click="addConstraintRule">+ 添加约束规则</el-button>
            </section>
          </div>
          </el-form>
      </div>
      <template #footer>
        <div class="drawer-footer">
          <el-button @click="createDrawerVisible = false">取消</el-button>
          <el-button type="primary" @click="submitCreateTask" :loading="creating">
            创建任务
          </el-button>
        </div>
      </template>
    </el-drawer>

    <!-- Monitor Tab Drawer -->
    <el-drawer v-model="monitorDrawerVisible" :title="`任务详情: ${activeTask?.taskName || ''}`" size="680px">
      <div v-if="activeTask" class="monitor-container">
        <!-- Meta Cards -->
        <div class="monitor-header-card">
          <div class="monitor-header-left">
            <div class="monitor-task-name">{{ activeTask.taskName }}</div>
            <div class="monitor-task-meta">
              <span>ID: {{ activeTask.taskId }}</span>
              <span>·</span>
              <span>流程: {{ getWorkflowName(activeTask.templateId) }}</span>
            </div>
          </div>
          <div class="monitor-header-right">
            <el-tag :type="getStatusType(activeTask.currentStatus)" size="large" effect="dark">
              {{ getStatusLabel(activeTask.currentStatus) }}
            </el-tag>
          </div>
        </div>
        <el-descriptions border :column="2" size="small" class="mb-4" style="margin-top: 12px;">
          <el-descriptions-item label="资源绑定数">
            <el-tag type="success" size="small" v-if="activeTask.resourceMap && Object.keys(activeTask.resourceMap || {}).length > 0">
              {{ Object.keys(activeTask.resourceMap || {}).length }} 个设备
            </el-tag>
            <span v-else style="color: #94a3b8;">未绑定</span>
          </el-descriptions-item>
          <el-descriptions-item label="当前节点">
            <el-tag type="info" size="small" v-if="activeTask.currentNodeIdRef != null">#{{ activeTask.currentNodeIdRef }}</el-tag>
            <span v-else style="color: #94a3b8;">-</span>
          </el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ formatTime(activeTask.startTime) }}</el-descriptions-item>
          <el-descriptions-item label="结束时间">{{ activeTask.endTime ? formatTime(activeTask.endTime) : '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- Drawer Content Tabs -->
        <el-tabs v-model="monitorActiveTab" class="monitor-tabs">
          <!-- Tab 1: Nodes Progress Map -->
          <el-tab-pane label="步骤执行" name="snapshots">
            <div class="snapshots-timeline" v-loading="loadingDetails">
              <div v-for="step in nodeSnapshots" :key="step.id" class="node-snapshot-card">
                <div class="node-snap-header">
                  <div style="display: flex; align-items: center; gap: 8px;">
                    <span class="item-index">{{ step.nodeIdRef }}</span>
                    <span class="node-name">深度 {{ step.stepDepth || 0 }}</span>
                  </div>
                  <el-tag :type="getNodeStateType(step.nodeStatus)" size="small" effect="plain">
                    {{ step.nodeStatus }}
                  </el-tag>
                </div>
                <div class="node-snap-body">
                  <div class="snap-row" v-if="step.startTime">
                    <span class="snap-label">开始：</span>
                    <span class="snap-val">{{ formatTime(step.startTime) }}</span>
                  </div>
                  <div class="snap-row" v-if="step.durationMs != null">
                    <span class="snap-label">耗时：</span>
                    <span class="snap-val">{{ step.durationMs }}ms</span>
                  </div>
                  <div class="snap-row" v-if="step.variableSpace && Object.keys(step.variableSpace).length > 0">
                    <span class="snap-label">变量空间：</span>
                    <div class="snap-tags mt-1">
                      <el-tag
                        v-for="(val, key) in step.variableSpace"
                        :key="key" type="info" size="small" class="mr-2 mb-1"
                      >
                        {{ key }}: {{ val }}
                      </el-tag>
                    </div>
                  </div>
                </div>
              </div>
              <el-empty v-if="nodeSnapshots.length === 0" description="暂无执行步骤记录" />
            </div>
          </el-tab-pane>

          <!-- Tab 2: Live Log Terminal -->
          <el-tab-pane label="执行日志" name="logs">
            <div class="terminal-header">
              <span>任务执行日志（自动刷新）</span>
              <el-button link type="primary" size="small" @click="fetchLogsAndSnapshots">
                手动同步
              </el-button>
            </div>
            <div class="log-container" ref="logContainerRef" v-loading="loadingDetails">
              <div v-for="log in executionLogs" :key="log.id" class="log-item">
                <span class="log-time">[{{ formatLogTime(log.logTime) }}]</span>
                <span class="log-module">[{{ log.sourceType || 'TASK' }}]</span>
                <span :class="['log-level', (log.logLevel || '').toLowerCase()]">{{ log.logLevel }}</span>
                <span class="log-msg">{{ log.logInfo }}</span>
              </div>
              <div v-if="executionLogs.length === 0" class="empty-terminal">
                &gt; 暂无任务日志。
              </div>
            </div>
          </el-tab-pane>

          <!-- Tab 3: Resource Map -->
          <el-tab-pane label="资源映射" name="resources">
            <div class="constraints-section">
              <div v-if="activeTask.resourceMap && Object.keys(activeTask.resourceMap || {}).length > 0">
                <div v-for="(instanceId, nodeRef) in activeTask.resourceMap" :key="nodeRef" class="resource-map-row">
                  <div class="resource-node-label">节点 #{{ nodeRef }}</div>
                  <el-icon style="color: #2563eb;"><ArrowRight /></el-icon>
                  <div class="resource-instance-label">
                    {{ getInstanceName(instanceId) }}
                    <span style="font-size: 11px; color: #94a3b8; margin-left: 6px;">(ID: {{ instanceId }})</span>
                  </div>
                </div>
              </div>
              <el-empty v-else description="未配置设备资源" />
            </div>
          </el-tab-pane>

          <!-- Tab 4: Supplemental constraints mapping -->
          <el-tab-pane label="任务约束" name="constraints">
            <div class="constraints-section" v-if="activeTask.globalConstraints">
              <div v-if="activeTask.globalConstraints.observableObjects_O?.length > 0" class="constraint-group">
                <div class="constraint-group-title">可观测对象</div>
                <el-tag v-for="obj in activeTask.globalConstraints.observableObjects_O" :key="obj" type="info" size="small" style="margin: 2px;">{{ obj }}</el-tag>
              </div>
              <div v-if="activeTask.globalConstraints.handlingActions_H?.length > 0" class="constraint-group">
                <div class="constraint-group-title">操作动作</div>
                <el-tag v-for="act in activeTask.globalConstraints.handlingActions_H" :key="act" type="warning" size="small" style="margin: 2px;">{{ act }}</el-tag>
              </div>
              <div v-if="activeTask.globalConstraints.globalConstraints_B0?.length > 0" class="constraint-group">
                <div class="constraint-group-title">全局约束</div>
                <el-tag v-for="c in activeTask.globalConstraints.globalConstraints_B0" :key="c" type="danger" size="small" style="margin: 2px;">{{ c }}</el-tag>
              </div>
              <div v-if="activeTask.globalConstraints.taskRequirements_U?.goals_G?.length > 0" class="constraint-group">
                <div class="constraint-group-title">任务目标</div>
                <el-tag v-for="g in activeTask.globalConstraints.taskRequirements_U.goals_G" :key="g" type="success" size="small" style="margin: 2px;">{{ g }}</el-tag>
              </div>
              <div v-if="activeTask.globalConstraints.taskRequirements_U?.taskConstraints_Btau?.length > 0" class="constraint-group">
                <div class="constraint-group-title">任务限制</div>
                <el-tag v-for="c in activeTask.globalConstraints.taskRequirements_U.taskConstraints_Btau" :key="c" size="small" style="margin: 2px;">{{ c }}</el-tag>
              </div>
              <el-empty v-if="!hasAnyConstraint(activeTask.globalConstraints)" description="未配置任务约束" />
            </div>
            <el-empty v-else description="未配置任务约束" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { Plus, Refresh, Delete, ArrowRight } from '@element-plus/icons-vue'

interface TaskInstance {
  taskId: number
  taskName: string
  taskDesc?: string
  templateId: string
  flowModelId?: number
  globalConstraints: Record<string, any>
  resourceMap?: Record<string, any>
  taskVariables?: Record<string, any>
  currentStatus: string
  currentNodeIdRef?: number
  currentFlowNodeId?: number
  startTime: string
  endTime?: string
}

interface TaskStep {
  id: number
  taskId: number
  flowNodeId?: number
  nodeIdRef?: number
  parentStepId?: number
  stepDepth?: number
  nodeStatus: string
  interfaceInSnapshot?: any
  interfaceOutSnapshot?: any
  portInSnapshot?: any
  portOutSnapshot?: any
  variableSpace?: Record<string, any>
  startTime?: string
  endTime?: string
  durationMs?: number
}

interface StepLog {
  id: number
  sourceType?: string
  taskId: number
  deviceInstanceId?: number
  taskStepId?: number
  logLevel: string
  logInfo: string
  logTime: string
}

interface WorkflowTemplate {
  templateId: string
  templateName: string
}

// Stats & lists
const tasks = ref<TaskInstance[]>([])
const loading = ref(false)
const taskTotal = ref(0)
const taskPageNo = ref(1)
const taskPageSize = ref(20)
const taskSummary = ref({
  total: 0,
  pending: 0,
  running: 0,
  completed: 0,
  failed: 0,
  aborted: 0
})
const processTemplates = ref<WorkflowTemplate[]>([])
const loadingWorkflows = ref(false)

// Drawer state
const createDrawerVisible = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref({
  taskName: '',
  templateId: '',
  resourceMap: {} as Record<string, string>,
  constraintsForm: {
    observableObjects: [] as any[],
    constraints: [] as any[]
  }
})
const creating = ref(false)
const allModels = ref<any[]>([])
const modelAttributes = ref<Record<string, string[]>>({})

const addObservableObject = () => {
  createForm.value.constraintsForm.observableObjects.push({
    name: '',
    sourceType: 'DEVICE_ATTRIBUTE',
    dataType: 'FLOAT',
    deviceModelId: undefined,
    deviceInstanceId: undefined,
    targetName: ''
  })
}

const addConstraintRule = () => {
  createForm.value.constraintsForm.constraints.push({
    name: '',
    observedObjectName: '',
    operator: 'EQ',
    threshold: '',
    violationActions: []
  })
}

const fetchModelAttributes = async (row: any) => {
  if (!row.deviceModelId) return
  if (!modelAttributes.value[row.deviceModelId]) {
    try {
      const res = await axios.get(`/api/device/model/${row.deviceModelId}`)
      if (res.data?.success && res.data.data?.modelDef?.attributes) {
        modelAttributes.value[row.deviceModelId] = res.data.data.modelDef.attributes.map((a: any) => a.attributeName)
      } else {
        modelAttributes.value[row.deviceModelId] = []
      }
      
      if (!availableInstances.value[row.deviceModelId]) {
        const listRes = await axios.get(`/api/device/instance/page?modelId=${row.deviceModelId}&pageSize=100`)
        if (listRes.data?.success) {
          availableInstances.value[row.deviceModelId] = listRes.data.data.records || []
        } else {
          availableInstances.value[row.deviceModelId] = []
        }
      }
    } catch (e) {
      modelAttributes.value[row.deviceModelId] = []
    }
  }
}

const fetchAllModels = async () => {
  try {
    const res = await axios.get('/api/device/model/list')
    if (res.data?.success) {
      allModels.value = res.data.data || []
    }
  } catch (e) {}
}

interface RequiredModel {
  nodeId: number
  nodeIdRef: number
  nodeType: string
  deviceModelId: string
}
const requiredModels = ref<RequiredModel[]>([])
const availableInstances = ref<Record<string, any[]>>({})

const handleTemplateChange = async (val: string) => {
  requiredModels.value = []
  createForm.value.resourceMap = {}
  
  if (!val) return
  try {
    const res = await axios.get(`/api/workflow/node/list?flowModelId=${val}`)
    if (res.data?.success && res.data.data) {
      const nodes = res.data.data || []
      const reqModels: RequiredModel[] = []
      
      for (const n of nodes) {
        if (n.deviceModelId) {
          reqModels.push({
            nodeId: n.id,
            nodeIdRef: n.nodeIdRef,
            nodeType: n.nodeType,
            deviceModelId: n.deviceModelId
          })
          
          if (!availableInstances.value[n.deviceModelId]) {
            try {
              const listRes = await axios.get(`/api/device/instance/page?modelId=${n.deviceModelId}&pageSize=100`)
              if (listRes.data?.success) {
                availableInstances.value[n.deviceModelId] = listRes.data.data.records || []
              } else {
                availableInstances.value[n.deviceModelId] = []
              }
            } catch (e) {
              availableInstances.value[n.deviceModelId] = []
            }
          }
        }
      }
      requiredModels.value = reqModels
    }
  } catch (error) {
    ElMessage.error('加载流程节点失败')
  }
}


// Monitor Drawer
const monitorDrawerVisible = ref(false)
const activeTask = ref<TaskInstance | null>(null)
const monitorActiveTab = ref('snapshots')
const nodeSnapshots = ref<TaskStep[]>([])
const executionLogs = ref<StepLog[]>([])
const loadingDetails = ref(false)
const logContainerRef = ref<HTMLElement | null>()
// All device instances cache for name lookup
const allInstances = ref<Record<string, string>>({})

// Auto refresh interval id
let pollIntervalId: any = null
let mainListPollIntervalId: any = null
let latestDetailLogId = 0

// Form rules
const createRules = ref<FormRules>({
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  templateId: [{ required: true, message: '请选择流程', trigger: 'change' }]
})

// Stats computed
const stats = computed(() => {
  return {
    total: taskSummary.value.total,
    running: taskSummary.value.running,
    completed: taskSummary.value.completed,
    failed: taskSummary.value.failed + taskSummary.value.aborted
  }
})

// Fetch all task instances
const fetchTasks = async () => {
  loading.value = true
  try {
    const res = await axios.get('/api/task/page', {
      params: {
        pageNo: taskPageNo.value,
        pageSize: taskPageSize.value
      }
    })
    if (res.data?.success) {
      const pageData = res.data.data || {}
      tasks.value = pageData.records || []
      taskTotal.value = pageData.total || 0
    } else {
      ElMessage.error(res.data?.message || '加载任务列表失败')
    }
  } catch (error: any) {
    ElMessage.error('无法加载任务数据: ' + error.message)
  } finally {
    loading.value = false
  }
}

// Fetch all device instances for name lookup
const fetchAllInstances = async () => {
  try {
    const res = await axios.get('/api/device/instance/page?pageSize=500')
    if (res.data?.success) {
      const records = res.data.data?.records || []
      const map: Record<string, string> = {}
      records.forEach((inst: any) => {
        map[String(inst.id)] = inst.instanceName || inst.deviceName || String(inst.id)
      })
      allInstances.value = map
    }
  } catch (e) {}
}

const getInstanceName = (instanceId: any) => {
  return allInstances.value[String(instanceId)] || String(instanceId)
}

const fetchTaskSummary = async () => {
  try {
    const res = await axios.get('/api/task/summary')
    if (res.data?.success) {
      taskSummary.value = {
        total: res.data.data?.total || 0,
        pending: res.data.data?.pending || 0,
        running: res.data.data?.running || 0,
        completed: res.data.data?.completed || 0,
        failed: res.data.data?.failed || 0,
        aborted: res.data.data?.aborted || 0
      }
    }
  } catch (error) {
    ElMessage.error('加载任务统计失败')
  }
}

const refreshTaskList = async () => {
  await Promise.all([fetchTasks(), fetchTaskSummary()])
}

const handleTaskPageChange = (page: number) => {
  taskPageNo.value = page
  fetchTasks()
}

const handleTaskPageSizeChange = (size: number) => {
  taskPageSize.value = size
  taskPageNo.value = 1
  fetchTasks()
}

// Fetch process workflows templates
const fetchWorkflows = async () => {
  loadingWorkflows.value = true
  try {
    const res = await axios.get('/api/workflow/list')
    if (res.data?.success) {
      processTemplates.value = res.data.data || []
    }
  } catch (error) {
    ElMessage.error('加载流程列表失败')
  } finally {
    loadingWorkflows.value = false
  }
}

// Workflow Name mapping helper
const getWorkflowName = (tplId: string) => {
  const match = processTemplates.value.find(w => w.templateId === tplId)
  return match ? match.templateName : tplId
}

// Status Badges helpers
const getStatusType = (status: string) => {
  switch (status) {
    case 'PENDING': return 'info'
    case 'RUNNING': return 'primary'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'ABORTED': return 'warning'
    default: return 'info'
  }
}

const getStatusLabel = (status: string) => {
  switch (status) {
    case 'PENDING': return '排队中'
    case 'RUNNING': return '运行中'
    case 'COMPLETED': return '完成'
    case 'FAILED': return '终止'
    case 'ABORTED': return '已终止'
    default: return status
  }
}

const getNodeStateType = (state: string) => {
  switch (state) {
    case 'PENDING': return 'info'
    case 'RUNNING': return 'warning'
    case 'COMPLETED': return 'success'
    case 'FAILED': return 'danger'
    case 'ABORTED': return 'warning'
    default: return 'info'
  }
}

// Row click trigger Drawer
const handleRowClick = (row: TaskInstance) => {
  activeTask.value = row
  monitorActiveTab.value = 'snapshots'
  nodeSnapshots.value = []
  executionLogs.value = []
  latestDetailLogId = 0
  monitorDrawerVisible.value = true
  
  fetchLogsAndSnapshots()
  
  // Start polling detail details
  startDetailsPolling()
}

// Start details polling
const startDetailsPolling = () => {
  stopDetailsPolling()
  pollIntervalId = setInterval(() => {
    if (monitorDrawerVisible.value && activeTask.value) {
      // If task is running, actively pull progress
      fetchLogsAndSnapshots(true)
    } else {
      stopDetailsPolling()
    }
  }, 3000)
}

const stopDetailsPolling = () => {
  if (pollIntervalId) {
    clearInterval(pollIntervalId)
    pollIntervalId = null
  }
}

// Fetch logs and snapshots for active task
const fetchLogsAndSnapshots = async (silent = false) => {
  if (!activeTask.value) return
  if (!silent) {
    loadingDetails.value = true
  }
  
  try {
    const taskId = activeTask.value.taskId
    
    // Concurrently load snapshots & logs
    const logsUrl = latestDetailLogId > 0
      ? `/api/task/logs/${taskId}?afterLogId=${latestDetailLogId}&limit=300`
      : `/api/task/logs/${taskId}`
    const [snapRes, logRes] = await Promise.all([
      axios.get(`/api/task/snapshots/${taskId}`),
      axios.get(logsUrl)
    ])
    
    if (snapRes.data?.success) {
      nodeSnapshots.value = snapRes.data.data || []
    }
    
    if (logRes.data?.success) {
      const logs = logRes.data.data || []
      if (latestDetailLogId > 0) {
        executionLogs.value = [...executionLogs.value, ...logs]
      } else {
        executionLogs.value = logs
      }
      latestDetailLogId = executionLogs.value.reduce((max, log) => Math.max(max, Number(log.id || 0)), latestDetailLogId)
      
      // Auto scroll terminal to bottom
      nextTick(() => {
        if (logContainerRef.value) {
          logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight
        }
      })
    }
    
    // Also sync the task instance itself in case status updated
    const taskRes = await axios.get(`/api/task/${taskId}`)
    if (taskRes.data?.success && taskRes.data.data) {
      const updated = taskRes.data.data
      activeTask.value.currentStatus = updated.currentStatus
      activeTask.value.startTime = updated.startTime
      activeTask.value.endTime = updated.endTime
      
      // Find matching item in main list and sync
      const mainMatch = tasks.value.find(t => t.taskId === taskId)
      if (mainMatch) {
        mainMatch.currentStatus = updated.currentStatus
        mainMatch.startTime = updated.startTime
        mainMatch.endTime = updated.endTime
      }
    }
  } catch (error) {
    ElMessage.error('加载任务详情失败')
  } finally {
    if (!silent) {
      loadingDetails.value = false
    }
  }
}

// Start task on engine
const startTask = async (taskId: number) => {
  try {
    await ElMessageBox.confirm('确认启动该任务吗？', '启动确认', {
      confirmButtonText: '确认启动',
      cancelButtonText: '取消',
      type: 'warning'
    })
    
    const res = await axios.post(`/api/task/start/${taskId}`)
    if (res.data?.success) {
      ElMessage.success('任务已启动')
      refreshTaskList()
    } else {
      ElMessage.error(res.data?.message || '启动任务失败')
    }
  } catch (e) {
    // cancelled
  }
}

const abortTask = async (taskId: number) => {
  try {
    await ElMessageBox.confirm('确认终止该任务吗？活动节点会停止推进，并释放已占用的设备资源。', '终止确认', {
      confirmButtonText: '确认终止',
      cancelButtonText: '取消',
      type: 'warning'
    })
    const res = await axios.post(`/api/task/abort/${taskId}`)
    if (res.data?.success) {
      ElMessage.success('任务已终止')
      await refreshTaskList()
      if (activeTask.value?.taskId === taskId) {
        await fetchLogsAndSnapshots()
      }
    } else {
      ElMessage.error(res.data?.message || '终止任务失败')
    }
  } catch (e) {
    // cancelled
  }
}

// Confirm Delete Task
const confirmDeleteTask = async (taskId: number) => {
  try {
    await ElMessageBox.confirm('确认删除该任务及关联快照吗？该操作不可恢复。', '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'danger'
    })
    
    const res = await axios.delete(`/api/task/delete/${taskId}`)
    if (res.data?.success) {
      ElMessage.success('任务已删除')
      if (activeTask.value?.taskId === taskId) {
        monitorDrawerVisible.value = false
      }
      refreshTaskList()
    } else {
      ElMessage.error(res.data?.message || '注销失败')
    }
  } catch (e) {
    // cancelled
  }
}

const openCreateDrawer = () => {
  createForm.value = {
    taskName: '',
    templateId: '',
    resourceMap: {},
    constraintsForm: {
      observableObjects: [],
      constraints: []
    }
  } as any
  requiredModels.value = []
  createDrawerVisible.value = true
  nextTick(() => {
    createFormRef.value?.clearValidate()
  })
}

// Submit Create task
const submitCreateTask = async () => {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (valid) {
      creating.value = true
      try {
        const payload = {
          taskName: createForm.value.taskName,
          flowModelId: createForm.value.templateId,
          globalConstraints: createForm.value.constraintsForm,
          resourceMap: createForm.value.resourceMap
        }
        
        const res = await axios.post('/api/task/save', payload)
        if (res.data?.success) {
          ElMessage.success('任务创建成功')
          createDrawerVisible.value = false
          taskPageNo.value = 1
          refreshTaskList()
        } else {
          ElMessage.error(res.data?.message || '任务创建失败')
        }
      } catch (error: any) {
        ElMessage.error('保存失败: ' + error.message)
      } finally {
        creating.value = false
      }
    }
  })
}

// Format time utility

const formatTime = (timeStr: string) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  return date.toLocaleString()
}

const formatLogTime = (timeStr: string) => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  return [
    String(date.getHours()).padStart(2, '0'),
    String(date.getMinutes()).padStart(2, '0'),
    String(date.getSeconds()).padStart(2, '0')
  ].join(':')
}

const startMainListPolling = () => {
  if (mainListPollIntervalId) return
  mainListPollIntervalId = setInterval(() => {
    // Only fetch tasks in background quietly
    axios.get(`/api/task/page?pageNo=${taskPageNo.value}&pageSize=${taskPageSize.value}`)
      .then((res) => {
        if (res.data?.success) {
          tasks.value = res.data.data.records || []
          taskTotal.value = res.data.data.total || 0
        }
      })
      .catch(() => {})
      
    axios.get('/api/task/summary').then((res) => {
      if (res.data?.success) taskSummary.value = res.data.data
    }).catch(() => {})
  }, 3000)
}

const stopMainListPolling = () => {
  if (mainListPollIntervalId) {
    clearInterval(mainListPollIntervalId)
    mainListPollIntervalId = null
  }
}

const hasAnyConstraint = (c: Record<string, any>) => {
  if (!c) return false
  return (
    (c.observableObjects_O?.length > 0) ||
    (c.handlingActions_H?.length > 0) ||
    (c.globalConstraints_B0?.length > 0) ||
    (c.taskRequirements_U?.goals_G?.length > 0) ||
    (c.taskRequirements_U?.taskConstraints_Btau?.length > 0)
  )
}

onMounted(() => {
  refreshTaskList()
  fetchWorkflows()
  fetchAllInstances()
  fetchAllModels()
  startMainListPolling()
})

onUnmounted(() => {
  stopDetailsPolling()
  stopMainListPolling()
})
</script>

<style scoped>
.task-list {
  padding: 32px;
  background-color: #f1f5f9;
  min-height: 100vh;
  box-sizing: border-box;
  font-family: "Inter", "PingFang SC", "Microsoft YaHei", sans-serif;
}

.header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
}

.title {
  margin: 0;
  color: #0f172a;
  font-weight: 800;
  font-size: 28px;
  letter-spacing: -0.03em;
}

.subtitle {
  margin: 6px 0 0 0;
  color: #64748b;
  font-size: 14px;
}

.mb-6 {
  margin-bottom: 32px;
}

.mb-4 {
  margin-bottom: 16px;
}

.mt-1 { margin-top: 4px; }
.mt-2 { margin-top: 8px; }
.mr-1 { margin-right: 4px; }
.mr-2 { margin-right: 8px; }
.mb-1 { margin-bottom: 4px; }

/* Premium Glassmorphism Stat Cards */
.stat-card {
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.8);
  border-radius: 16px;
  padding: 24px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.03);
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1), box-shadow 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px rgba(0, 0, 0, 0.08);
}

.stat-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.stat-val {
  font-size: 36px;
  font-weight: 800;
  color: #0f172a;
  margin-top: 8px;
}

/* Gradient Texts for Val */
.text-blue {
  background: linear-gradient(135deg, #3b82f6 0%, #06b6d4 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.text-green {
  background: linear-gradient(135deg, #10b981 0%, #34d399 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}
.text-red {
  background: linear-gradient(135deg, #f43f5e 0%, #fb923c 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

/* Table Card */
.table-card {
  background: #ffffff;
  border: none;
  border-radius: 16px;
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  font-size: 16px;
  color: #1e293b;
  padding: 16px 20px;
}

/* Custom Table Styles */
.custom-table {
  border-radius: 0 0 16px 16px;
  overflow: hidden;
}

.custom-table :deep(th.el-table__cell) {
  background-color: #f8fafc;
  color: #475569;
  font-weight: 600;
  text-transform: uppercase;
  font-size: 12px;
  letter-spacing: 0.05em;
  border-bottom: 2px solid #e2e8f0;
}

.custom-table :deep(td.el-table__cell) {
  border-bottom: 1px solid #f1f5f9;
  padding: 12px 0;
}

.custom-table :deep(.el-table__row) {
  transition: background-color 0.2s ease;
}

.custom-table :deep(.el-table__row:hover > td.el-table__cell) {
  background-color: #f8fafc;
}

.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding: 20px;
}

/* Status Badge with Neon Pulse */
.status-badge-container {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.status-tag {
  border-radius: 20px;
  font-weight: 600;
  border: none;
  padding: 0 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.05);
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #3b82f6;
  box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7);
  animation: pulse 1.6s infinite cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes pulse {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(59, 130, 246, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(59, 130, 246, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(59, 130, 246, 0); }
}

.time-range-cell {
  font-size: 12px;
  color: #64748b;
  line-height: 1.6;
}

.time-label {
  color: #94a3b8;
  font-weight: 500;
}

.action-buttons {
  display: flex;
  justify-content: center;
  gap: 8px;
}

.premium-btn {
  background: linear-gradient(135deg, #2563eb 0%, #3b82f6 100%);
  border: none;
  border-radius: 8px;
  font-weight: 600;
  padding: 10px 20px;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.2);
}
.premium-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(37, 99, 235, 0.3);
}

/* Forms */
.create-form {
  padding: 10px 20px;
}

.monospace-textarea :deep(textarea) {
  font-family: "Fira Code", Consolas, Monaco, monospace;
  font-size: 13px;
  background-color: #f8fafc;
  color: #0f172a;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.schema-info-bar {
  background: linear-gradient(90deg, #eff6ff 0%, #f8fafc 100%);
  border-left: 4px solid #3b82f6;
  padding: 10px 14px;
  font-size: 12px;
  color: #1e3a8a;
  border-radius: 0 6px 6px 0;
}

.drawer-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 20px;
}

/* Monitor Drawer */
.monitor-container {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
  padding: 0 20px;
}

.monitor-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}

:deep(.el-tabs__content) {
  flex: 1;
  overflow-y: auto;
  padding-top: 16px;
}

/* Timeline Snapshots */
.snapshots-timeline {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding-right: 8px;
}

.node-snapshot-card {
  background: #ffffff;
  border: 1px solid #f1f5f9;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.02);
  transition: transform 0.2s ease;
  position: relative;
  overflow: hidden;
}
.node-snapshot-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: #3b82f6;
  border-radius: 4px 0 0 4px;
}

.node-snap-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  border-bottom: 1px dashed #e2e8f0;
  margin-bottom: 12px;
}

.dynamic-list-item {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  width: 100%;
}

.drawer-body {
  height: 100%;
  min-height: 0;
  overflow-y: auto;
}

.drawer-section {
  border: 1px solid #e5e7eb;
  border-radius: 4px;
  padding: 10px 16px;
  background: #fff;
  margin-top: 8px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  min-height: 26px;
}

.section-title h3 {
  margin: 0;
  font-size: 15px;
  line-height: 1.3;
  color: #0f172a;
}

.compact-empty {
  display: flex;
  align-items: center;
  min-height: 30px;
  padding: 7px 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  background: #f8fafc;
  color: #64748b;
  font-size: 13px;
}

.node-name {
  font-size: 14px;
  font-weight: 700;
  color: #0f172a;
}

.node-snap-body {
  font-size: 13px;
}

.snap-row {
  margin-bottom: 8px;
  display: flex;
  flex-direction: column;
}

.snap-label {
  color: #64748b;
  font-weight: 600;
  margin-bottom: 4px;
  text-transform: uppercase;
  font-size: 11px;
}

.snap-val {
  color: #0f172a;
  font-weight: 500;
}

.snap-tags {
  display: flex;
  flex-wrap: wrap;
}

/* Terminal Log UI */
.terminal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 12px;
  font-weight: 600;
  text-transform: uppercase;
}

.log-container {
  background-color: #f8fafc;
  color: #334155;
  padding: 20px;
  border-radius: 12px;
  font-family: "Fira Code", Consolas, Monaco, monospace;
  font-size: 13px;
  height: 420px;
  overflow-y: auto;
  line-height: 1.6;
  border: 1px solid #e2e8f0;
  box-shadow: inset 0 2px 10px rgba(0,0,0,0.02);
}

.log-item {
  margin-bottom: 8px;
  word-wrap: break-word;
}

.log-time {
  color: #64748b;
  margin-right: 12px;
}

.log-module {
  color: #0284c7;
  margin-right: 12px;
  font-weight: 600;
}

.log-level {
  display: inline-block;
  width: 60px;
  font-weight: 700;
  text-transform: uppercase;
  margin-right: 12px;
}
.log-level.info { color: #3b82f6; }
.log-level.warn { color: #f59e0b; }
.log-level.error { color: #ef4444; }
.log-level.success { color: #10b981; }

.log-msg {
  color: #0f172a;
}

.empty-terminal {
  color: #475569;
  font-style: italic;
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.constraints-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 4px 0;
}

.constraint-group {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 16px;
}

.constraint-group-title {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}

/* Monitor header card */
.monitor-header-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 20px;
  margin-bottom: 4px;
}

.monitor-task-name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 4px;
}

.monitor-task-meta {
  display: flex;
  gap: 8px;
  font-size: 12px;
  color: #94a3b8;
}

/* Resource Map */
.resource-map-row {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 8px;
}

.resource-node-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  background: #f1f5f9;
  padding: 4px 10px;
  border-radius: 6px;
  font-family: monospace;
  min-width: 80px;
  text-align: center;
}

.resource-instance-label {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
  flex: 1;
}

/* item-index badge */
.item-index {
  width: 22px;
  height: 22px;
  flex: 0 0 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  background: #eef2f7;
  color: #475569;
  font-size: 12px;
  font-weight: 700;
}

</style>



````

---

## Frontend/src/views/task/WorkflowDesigner.vue

````text

<template>
  <div class="workflow-designer">
    <aside class="sidebar">
      <el-tabs v-model="sidebarTab" class="sidebar-tabs" stretch>
        <el-tab-pane label="节点组件库" name="palette">
          <div class="sidebar-scroll" v-loading="loadingBase">
            <div class="group-title">控制节点</div>
            <div class="logic-grid">
              <div
                v-for="item in logicTemplates"
                :key="item.kind"
                class="logic-item"
                draggable="true"
                @dragstart="onPaletteDragStart($event, item.kind)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </div>
            </div>

            <div class="group-title">设备模型节点</div>
            <div class="palette-list">
              <div
                v-for="model in deviceModels"
                :key="model.modelId"
                class="palette-item"
                draggable="true"
                @dragstart="onDeviceDragStart($event, model)"
              >
                <el-icon><Box /></el-icon>
                <div>
                  <div class="item-title">{{ model.modelName }}</div>
                  <div class="item-sub mono">{{ model.modelId }}</div>
                </div>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="已有流程" name="workflows">
          <div class="sidebar-scroll" v-loading="loadingWorkflows">
            <div
              v-for="wf in workflows"
              :key="workflowId(wf)"
              :class="['workflow-item', { active: selectedWorkflowId === workflowId(wf) }]"
              @click="loadWorkflow(workflowId(wf))"
            >
              <div class="item-title">{{ workflowNameText(wf) }}</div>
              <div class="item-sub mono">{{ workflowId(wf) }}</div>
            </div>
            <el-empty v-if="workflows.length === 0" description="暂无流程" :image-size="60" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </aside>

    <section class="main">
      <div class="header">
        <div class="header-left">
          <el-input v-model="workflowName" placeholder="请输入流程名称" class="name-input" />
          <div class="protocol-strip">
            <span>执行切片</span>
            <span>节点 {{ nodes.length }}</span>
            <span>控制流 {{ interfaceEdgeCount }}</span>
            <span>数据流 {{ portEdgeCount }}</span>
            <span>Sigma: smartlab.signal.v1</span>
          </div>
        </div>
        <div class="header-actions">
          <el-button @click="clearCanvas">清空画布</el-button>
          <el-button type="primary" :loading="saving" @click="saveWorkflow">保存流程</el-button>
        </div>
      </div>

      <div class="canvas-wrap" ref="flowContainer" @drop="onDrop" @dragover.prevent>
        <VueFlow
          v-model:nodes="nodes"
          v-model:edges="edges"
          :snap-to-grid="true"
          :snap-grid="[20, 20]"
          :default-edge-options="{ type: 'smoothstep', markerEnd: MarkerType.ArrowClosed }"
          @connect="onConnect"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
        >
          <Background :gap="20" pattern-color="#d7dbe1" />
          <Controls position="bottom-right" />

          <template #node-custom="props">
            <div class="node-shell" :class="`kind-${props.data.kind}`">
              <Handle
                v-for="(h, idx) in getFlowInHandles(props.data.interfaces)"
                :key="`if_in_${h.interfaceId}`"
                :id="`if:${h.interfaceId}`"
                type="target"
                :position="Position.Top"
                class="node-handle flow-handle"
                :style="{ left: `${(idx + 1) * (100 / (getFlowInHandles(props.data.interfaces).length + 1))}%` }"
              />
              <Handle
                v-for="(h, idx) in getFlowOutHandles(props.data.interfaces)"
                :key="`if_out_${h.interfaceId}`"
                :id="`if:${h.interfaceId}`"
                type="source"
                :position="Position.Bottom"
                class="node-handle flow-handle"
                :style="{ left: `${(idx + 1) * (100 / (getFlowOutHandles(props.data.interfaces).length + 1))}%` }"
              />
              <Handle
                v-for="(p, idx) in getDataInPorts(props.data.ports)"
                :key="`port_in_${p.portId}`"
                :id="`port:${p.portId}`"
                type="target"
                :position="Position.Left"
                class="node-handle data-handle"
                :style="{ top: calcOffset(idx, getDataInPorts(props.data.ports).length) }"
              />
              <Handle
                v-for="(p, idx) in getDataOutPorts(props.data.ports)"
                :key="`port_out_${p.portId}`"
                :id="`port:${p.portId}`"
                type="source"
                :position="Position.Right"
                class="node-handle data-handle"
                :style="{ top: calcOffset(idx, getDataOutPorts(props.data.ports).length) }"
              />

              <div class="node-head">
                <span class="node-tag">{{ kindLabel(props.data.kind) }}</span>
                <span class="node-title">{{ props.data.name }}</span>
              </div>
              <div class="node-sub">{{ nodeSummary(props.data) }}</div>
              <div class="node-meta">
                <span>接口 {{ props.data.interfaces.length }}</span>
                <span>端口 {{ props.data.ports.length }}</span>
                <span v-if="props.data.kind === 'device'">信号 {{ props.data.functionId || '未配置' }}</span>
              </div>
            </div>
          </template>
        </VueFlow>
      </div>
    </section>

    <el-drawer v-model="drawerVisible" title="节点配置" size="620px">
      <div v-if="selectedNode" class="drawer-content">
        <el-form label-width="100px" size="small">
          <el-form-item label="节点名称"><el-input v-model="selectedNode.data.name" /></el-form-item>
          <el-form-item label="节点类型"><el-input :model-value="kindLabel(selectedNode.data.kind)" disabled /></el-form-item>
        </el-form>

        <el-tabs v-model="configTab" class="config-tabs">
          <el-tab-pane label="基础配置" name="basic">
            <el-form label-width="110px" size="small">
              <template v-if="selectedNode.data.kind === 'device'">
                <el-form-item label="设备模型">
                  <el-select v-model="selectedNode.data.modelId" style="width: 100%" @change="onModelChanged">
                    <el-option v-for="m in deviceModels" :key="m.modelId" :label="m.modelName" :value="m.modelId" />
                  </el-select>
                </el-form-item>
                <el-form-item label="执行能力">
                  <el-select v-model="selectedNode.data.functionId" style="width: 100%" @change="onFunctionChanged">
                    <el-option
                      v-for="op in selectedNodeOps"
                      :key="capabilityOptionValue(op)"
                      :label="capabilityOptionLabel(op)"
                      :value="capabilityOptionValue(op)"
                    />
                  </el-select>
                </el-form-item>
                <el-form-item label="协议预览">
                  <div class="signal-preview">
                    <div><span>Envelope</span><b>smartlab.signal.v1</b></div>
                    <div><span>输入接口</span><b>if_wf_cmd_in</b></div>
                    <div><span>信号类型</span><b>{{ selectedNode.data.functionId || '未选择' }}</b></div>
                    <div><span>目标命令</span><b>{{ selectedCommandId || '-' }}</b></div>
                  </div>
                </el-form-item>
                <el-form-item label="能力参数">
                  <el-table :data="selectedNodeParamRows" border size="small" style="width: 100%">
                    <el-table-column prop="key" label="参数" width="180" />
                    <el-table-column label="值">
                      <template #default="{ row }"><el-input v-model="selectedNode.data.parameters[row.key]" /></template>
                    </el-table-column>
                  </el-table>
                </el-form-item>
              </template>

              <template v-else-if="selectedNode.data.kind === 'branch'">
                <el-form-item label="分支表达式">
                  <div class="condition-list">
                    <div v-for="c in selectedNode.data.conditions" :key="c.interfaceId" class="condition-row">
                      <span class="condition-label">{{ c.label }}</span>
                      <el-input v-model="c.expression" placeholder="例如 temperature >= 80" />
                    </div>
                  </div>
                </el-form-item>
              </template>
            </el-form>
          </el-tab-pane>
          <el-tab-pane label="端口与变量" name="ports">
            <el-divider content-position="left">内部变量</el-divider>
            <div class="table-actions"><el-button size="small" @click="addInternalVariable">新增变量</el-button></div>
            <el-table :data="selectedNode.data.internalVariables" border size="small" style="width: 100%">
              <el-table-column label="变量名">
                <template #default="{ row }"><el-input v-model="row.name" /></template>
              </el-table-column>
              <el-table-column label="类型" width="140">
                <template #default="{ row }">
                  <el-select v-model="row.dataType" style="width: 100%">
                    <el-option label="字符串" value="string" />
                    <el-option label="数字" value="number" />
                    <el-option label="布尔" value="boolean" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="映射" width="180">
                <template #default="{ row }"><el-input v-model="row.mapping" placeholder="可选" /></template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }"><el-button link type="danger" @click="removeInternalVariable($index)">删除</el-button></template>
              </el-table-column>
            </el-table>

            <el-divider content-position="left">数据端口</el-divider>
            <div class="table-actions"><el-button size="small" @click="addPort">新增端口</el-button></div>
            <el-table :data="selectedNode.data.ports" border size="small" style="width: 100%">
              <el-table-column label="端口ID" width="150">
                <template #default="{ row }"><el-input v-model="row.portId" /></template>
              </el-table-column>
              <el-table-column label="方向" width="110">
                <template #default="{ row }">
                  <el-select v-model="row.direction" style="width: 100%">
                    <el-option label="输入" value="IN" />
                    <el-option label="输出" value="OUT" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="类型" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.dataType" style="width: 100%">
                    <el-option label="字符串" value="string" />
                    <el-option label="数字" value="number" />
                    <el-option label="布尔" value="boolean" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="绑定变量">
                <template #default="{ row }">
                  <el-select v-model="row.variableBinding" style="width: 100%" clearable>
                    <el-option v-for="v in selectedNode.data.internalVariables" :key="v.name" :label="v.name" :value="v.name" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="80">
                <template #default="{ $index }"><el-button link type="danger" @click="removePort($index)">删除</el-button></template>
              </el-table-column>
            </el-table>
          </el-tab-pane>

          <el-tab-pane label="接口与触发" name="interfaces">
            <div class="table-actions"><el-button size="small" @click="addInterface">新增接口</el-button></div>
            <el-table :data="selectedNode.data.interfaces" border size="small" style="width: 100%">
              <el-table-column label="接口ID" width="140"><template #default="{ row }"><el-input v-model="row.interfaceId" /></template></el-table-column>
              <el-table-column label="方向" width="100">
                <template #default="{ row }">
                  <el-select v-model="row.direction" style="width: 100%">
                    <el-option label="输入" value="IN" />
                    <el-option label="输出" value="OUT" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="信号" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.signalType" style="width: 100%">
                    <el-option label="START" value="START" />
                    <el-option label="DONE" value="DONE" />
                    <el-option label="ERROR" value="ERROR" />
                    <el-option label="ALERT" value="ALERT" />
                    <el-option label="USER_CONFIRM" value="USER_CONFIRM" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="触发方式" width="120">
                <template #default="{ row }">
                  <el-select v-model="row.triggerMode" style="width: 100%">
                    <el-option label="总是触发" value="ALWAYS" />
                    <el-option label="按信号" value="SIGNAL" />
                    <el-option label="按表达式" value="EXPRESSION" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="触发条件">
                <template #default="{ row }"><el-input v-model="row.triggerExpr" :disabled="row.triggerMode !== 'EXPRESSION'" /></template>
              </el-table-column>
              <el-table-column label="操作" width="80"><template #default="{ $index }"><el-button link type="danger" @click="removeInterface($index)">删除</el-button></template></el-table-column>
            </el-table>
            <div class="hint">控制流接口用于流程推进；数据端口用于节点间数据交换。两类连接会分别入库。</div>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { Box, CircleCheck, Connection, Share, VideoPlay } from '@element-plus/icons-vue'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { Handle, MarkerType, Position, VueFlow, useVueFlow } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'

type DeviceModel = {
  modelId: string
  modelName: string
  capabilitySpec?: {
    operations?: Array<{ name: string; description?: string; parameters?: Record<string, any> | Array<{ name?: string; paramName?: string; id?: string }> }>
    capabilities?: Array<{
      capabilityId: string
      adapterCommandId: string
      name: string
      displayName?: string
      parameters?: Array<{ id?: string; name?: string; displayName?: string }>
    }>
  }
}

type FlowNodeKind = 'start' | 'end' | 'branch' | 'join' | 'device'
type NodeVariable = { name: string; dataType: 'string' | 'number' | 'boolean'; mapping: string }
type NodePort = { portId: string; direction: 'IN' | 'OUT'; dataType: 'string' | 'number' | 'boolean'; variableBinding: string }
type NodeInterface = {
  interfaceId: string
  direction: 'IN' | 'OUT'
  signalType: 'START' | 'DONE' | 'ERROR' | 'ALERT' | 'USER_CONFIRM'
  triggerMode: 'ALWAYS' | 'SIGNAL' | 'EXPRESSION'
  triggerExpr: string
}

type WorkflowNodeData = {
  kind: FlowNodeKind
  name: string
  nodeType: 'DEVICE_CAPABILITY_NODE' | 'FUNCTIONAL_NODE'
  modelId: string
  functionId: string
  parameters: Record<string, any>
  conditions: Array<{ label: string; expression: string; interfaceId: string }>
  interfaces: NodeInterface[]
  ports: NodePort[]
  internalVariables: NodeVariable[]
}

const sidebarTab = ref('palette')
const configTab = ref('basic')
const loadingBase = ref(false)
const loadingWorkflows = ref(false)
const saving = ref(false)

const workflowName = ref('')
const selectedWorkflowId = ref('')
const deviceModels = ref<DeviceModel[]>([])
const workflows = ref<any[]>([])
const flowContainer = ref<HTMLElement | null>(null)
const nodes = ref<any[]>([])
const edges = ref<any[]>([])
const selectedNodeId = ref('')
const drawerVisible = ref(false)

const { project, addEdges } = useVueFlow()

const logicTemplates = [
  { kind: 'start', label: '开始', icon: VideoPlay },
  { kind: 'end', label: '结束', icon: CircleCheck },
  { kind: 'branch', label: '分支', icon: Share },
  { kind: 'join', label: '汇聚', icon: Connection }
]

const selectedNode = computed(() => nodes.value.find(n => n.id === selectedNodeId.value) || null)
const modelMap = computed(() => {
  const map: Record<string, DeviceModel> = {}
  deviceModels.value.forEach(item => (map[item.modelId] = item))
  return map
})
const selectedNodeOps = computed(() => {
  if (!selectedNode.value?.data?.modelId) return [] as Array<{ name: string; parameters?: any }>
  const spec = modelMap.value[selectedNode.value.data.modelId]?.capabilitySpec || {}
  const caps = spec.capabilities || []
  if (caps.length > 0) return caps.map((cap: any) => ({
    name: cap.capabilityId || cap.name,
    description: cap.displayName || cap.name,
    adapterCommandId: cap.adapterCommandId,
    parameters: cap.parameters || []
  }))
  return spec.operations || []
})
const selectedNodeParamRows = computed(() => {
  if (!selectedNode.value?.data?.functionId) return [] as Array<{ key: string }>
  const op = selectedNodeOps.value.find((item: any) => capabilityOptionValue(item) === selectedNode.value?.data?.functionId)
  if (!op?.parameters) return []
  if (Array.isArray(op.parameters)) return op.parameters.map((p: any) => ({ key: p.name || p.paramName || p.id || '' })).filter(v => v.key)
  return Object.keys(op.parameters).map(key => ({ key }))
})
const selectedCommandId = computed(() => {
  const op = selectedNodeOps.value.find((item: any) => capabilityOptionValue(item) === selectedNode.value?.data?.functionId) as any
  return op?.adapterCommandId || op?.name || ''
})

const interfaceEdgeCount = computed(() => edges.value.filter((e: any) => String(e.sourceHandle || '').startsWith('if:')).length)
const portEdgeCount = computed(() => edges.value.filter((e: any) => String(e.sourceHandle || '').startsWith('port:')).length)

const workflowId = (wf: any) => wf.templateId || wf.id || ''
const workflowNameText = (wf: any) => wf.templateName || wf.name || '未命名流程'

const kindLabel = (kind: FlowNodeKind) => {
  if (kind === 'start') return '开始'
  if (kind === 'end') return '结束'
  if (kind === 'branch') return '分支'
  if (kind === 'join') return '汇聚'
  if (kind === 'device') return '设备'
  return '设备'
}

const nodeSummary = (data: WorkflowNodeData) => {
  if (data.kind === 'device') return `${modelMap.value[data.modelId]?.modelName || '未选模型'} / ${data.functionId || '未选能力'}`
  if (data.kind === 'branch') return `分支条件 ${data.conditions.length}`
  return '流程控制'
}

const capabilityOptionValue = (op: any) => op.capabilityId || op.name || op.commandId || ''
const capabilityOptionLabel = (op: any) => {
  const value = capabilityOptionValue(op)
  const command = op.adapterCommandId ? ` -> ${op.adapterCommandId}` : ''
  return `${op.displayName || op.description || op.name || value}${command}`
}

const getFlowInHandles = (interfaces: NodeInterface[] = []) => interfaces.filter(i => i.direction === 'IN')
const getFlowOutHandles = (interfaces: NodeInterface[] = []) => interfaces.filter(i => i.direction === 'OUT')
const getDataInPorts = (ports: NodePort[] = []) => ports.filter(p => p.direction === 'IN')
const getDataOutPorts = (ports: NodePort[] = []) => ports.filter(p => p.direction === 'OUT')
const calcOffset = (index: number, total: number) => `${(index + 1) * (100 / (total + 1))}%`

const defaultInterfacesByKind = (kind: FlowNodeKind): NodeInterface[] => {
  if (kind === 'start') return [{ interfaceId: 'flow_out', direction: 'OUT', signalType: 'START', triggerMode: 'ALWAYS', triggerExpr: '' }]
  if (kind === 'end') return [{ interfaceId: 'flow_in', direction: 'IN', signalType: 'DONE', triggerMode: 'SIGNAL', triggerExpr: '' }]
  if (kind === 'branch') return [
    { interfaceId: 'flow_in', direction: 'IN', signalType: 'DONE', triggerMode: 'SIGNAL', triggerExpr: '' },
    { interfaceId: 'flow_yes', direction: 'OUT', signalType: 'DONE', triggerMode: 'EXPRESSION', triggerExpr: 'result == true' },
    { interfaceId: 'flow_no', direction: 'OUT', signalType: 'DONE', triggerMode: 'EXPRESSION', triggerExpr: 'result == false' }
  ]
  return [
    { interfaceId: 'flow_in', direction: 'IN', signalType: 'START', triggerMode: 'SIGNAL', triggerExpr: '' },
    { interfaceId: 'flow_out', direction: 'OUT', signalType: 'DONE', triggerMode: 'ALWAYS', triggerExpr: '' }
  ]
}
const defaultPortsByKind = (kind: FlowNodeKind): NodePort[] => kind === 'device'
  ? [{ portId: 'data_in', direction: 'IN', dataType: 'number', variableBinding: '' }, { portId: 'data_out', direction: 'OUT', dataType: 'number', variableBinding: '' }]
  : []
const defaultVariablesByKind = (_kind: FlowNodeKind): NodeVariable[] => []

const createNodeData = (kind: FlowNodeKind, model?: DeviceModel): WorkflowNodeData => {
  if (kind === 'device') return {
    kind, name: `${model?.modelName || '设备'}节点`, nodeType: 'DEVICE_CAPABILITY_NODE', modelId: model?.modelId || '', functionId: '', parameters: {},
    conditions: [], interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind)
  }
  if (kind === 'branch') return {
    kind, name: '分支节点', nodeType: 'FUNCTIONAL_NODE', modelId: '', functionId: 'BRANCH_EVAL', parameters: {},
    conditions: [{ label: '满足条件', expression: 'result == true', interfaceId: 'flow_yes' }, { label: '不满足', expression: 'result == false', interfaceId: 'flow_no' }],
    interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind)
  }
  return { kind, name: `${kindLabel(kind)}节点`, nodeType: 'FUNCTIONAL_NODE', modelId: '', functionId: kind.toUpperCase(), parameters: {}, conditions: [], interfaces: defaultInterfacesByKind(kind), ports: defaultPortsByKind(kind), internalVariables: defaultVariablesByKind(kind) }
}

const onPaletteDragStart = (event: DragEvent, kind: FlowNodeKind) => {
  if (!event.dataTransfer) return
  event.dataTransfer.setData('nodeKind', kind)
}
const onDeviceDragStart = (event: DragEvent, model: DeviceModel) => {
  if (!event.dataTransfer) return
  event.dataTransfer.setData('nodeKind', 'device')
  event.dataTransfer.setData('deviceModel', JSON.stringify(model))
}

const onDrop = (event: DragEvent) => {
  const kind = (event.dataTransfer?.getData('nodeKind') || '') as FlowNodeKind
  if (!kind || !flowContainer.value) return
  const bounds = flowContainer.value.getBoundingClientRect()
  const position = project({ x: event.clientX - bounds.left, y: event.clientY - bounds.top })

  let model: DeviceModel | undefined
  if (kind === 'device') {
    const raw = event.dataTransfer?.getData('deviceModel')
    if (raw) {
      try { model = JSON.parse(raw) } catch { model = undefined }
    }
  }

  nodes.value.push({
    id: `node_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
    type: 'custom',
    position,
    data: createNodeData(kind, model)
  })
}

const onConnect = (params: any) => {
  const sourceHandle = String(params.sourceHandle || '')
  const targetHandle = String(params.targetHandle || '')
  const sourceIsInterface = sourceHandle.startsWith('if:')
  const targetIsInterface = targetHandle.startsWith('if:')
  const sourceIsPort = sourceHandle.startsWith('port:')
  const targetIsPort = targetHandle.startsWith('port:')

  if ((sourceIsInterface && !targetIsInterface) || (sourceIsPort && !targetIsPort)) {
    ElMessage.warning('控制流接口和数据端口不能混连')
    return
  }
  if (params.source === params.target) {
    ElMessage.warning('节点不能自连接')
    return
  }

  addEdges([{
    ...params,
    id: `edge_${Date.now()}_${Math.floor(Math.random() * 1000)}`,
    type: 'smoothstep',
    animated: sourceIsInterface,
    style: {
      stroke: sourceIsInterface ? '#6b7280' : '#8f6d2a',
      strokeWidth: 2,
      strokeDasharray: sourceIsInterface ? 'none' : '5,4'
    },
    markerEnd: MarkerType.ArrowClosed
  }])
}

const onNodeClick = ({ node }: any) => {
  selectedNodeId.value = node.id
  configTab.value = 'basic'
  drawerVisible.value = true
}
const onPaneClick = () => {
  drawerVisible.value = false
  selectedNodeId.value = ''
}

const onModelChanged = () => {
  if (!selectedNode.value) return
  selectedNode.value.data.functionId = ''
  selectedNode.value.data.parameters = {}
}
const onFunctionChanged = () => {
  if (!selectedNode.value) return
  const op = selectedNodeOps.value.find((item: any) => capabilityOptionValue(item) === selectedNode.value.data.functionId)
  const params: Record<string, any> = {}
  if (op?.parameters) {
    if (Array.isArray(op.parameters)) op.parameters.forEach((p: any) => { const key = p.name || p.paramName || p.id; if (key) params[key] = '' })
    else Object.keys(op.parameters).forEach(key => { params[key] = '' })
  }
  selectedNode.value.data.parameters = params
}

const addInternalVariable = () => {
  selectedNode.value?.data.internalVariables.push({ name: '', dataType: 'string', mapping: '' })
}
const removeInternalVariable = (index: number) => {
  selectedNode.value?.data.internalVariables.splice(index, 1)
}
const addPort = () => {
  selectedNode.value?.data.ports.push({ portId: `port_${Date.now().toString().slice(-4)}`, direction: 'IN', dataType: 'string', variableBinding: '' })
}
const removePort = (index: number) => {
  selectedNode.value?.data.ports.splice(index, 1)
}
const addInterface = () => {
  selectedNode.value?.data.interfaces.push({ interfaceId: `flow_${Date.now().toString().slice(-4)}`, direction: 'OUT', signalType: 'DONE', triggerMode: 'ALWAYS', triggerExpr: '' })
}
const removeInterface = (index: number) => {
  selectedNode.value?.data.interfaces.splice(index, 1)
}
const clearCanvas = () => {
  nodes.value = []
  edges.value = []
  drawerVisible.value = false
  selectedNodeId.value = ''
}

const buildPayload = () => {
  const nodesDef = nodes.value.map((n: any, idx: number) => {
    const interfaces = (n.data.interfaces || []).map((it: NodeInterface) => ({
      interfaceId: `${n.id}_${it.interfaceId}`,
      direction: it.direction,
      interfaceType: 'CONTROL_FLOW',
      allowedSignals: ['START', 'DONE', 'ERROR', 'ALERT', 'USER_CONFIRM'],
      triggerMode: it.triggerMode,
      triggerExpr: it.triggerExpr,
      signalType: it.signalType
    }))

    const ports = (n.data.ports || [])
      .filter((p: NodePort) => p.portId)
      .map((p: NodePort) => ({
        portId: `${n.id}_${p.portId}`,
        direction: p.direction,
        dataType: p.dataType,
        internalVariableBinding: p.variableBinding
      }))

    const internalVariables = (n.data.internalVariables || [])
      .filter((v: NodeVariable) => v.name)
      .map((v: NodeVariable, vi: number) => ({
        variableId: `${n.id}_var_${vi}`,
        name: v.name,
        dataType: v.dataType,
        mapping: v.mapping
      }))

    const capability =
      n.data.kind === 'device'
        ? { deviceModelRef: n.data.modelId, capabilityRef: n.data.functionId || 'NOOP', bindingPolicy: 'AUTO_ONLINE' }
        : { functionType: n.data.kind.toUpperCase(), capabilityRef: n.data.functionId || n.data.kind.toUpperCase() }

    const nodeParameters = n.data.kind === 'device' ? { ...n.data.parameters } : {}

    return {
      nodeId: n.id,
      name: n.data.name,
      nodeType: n.data.nodeType,
      capability,
      parameters: nodeParameters,
      internalVariables,
      lifecycle: {
        initialState: 'PENDING',
        states: [
          { stateId: 'PENDING', stateName: 'PENDING' },
          { stateId: 'RUNNING', stateName: 'RUNNING' },
          { stateId: 'SUCCESS', stateName: 'SUCCESS' },
          { stateId: 'FAILED', stateName: 'FAILED' }
        ],
        transitions: [
          { transitionId: `${n.id}_t1`, from: 'PENDING', to: 'RUNNING', trigger: { interfaceRef: `${n.id}_flow_in`, signalType: 'START' } },
          { transitionId: `${n.id}_t2`, from: 'RUNNING', to: 'SUCCESS', trigger: { interfaceRef: `${n.id}_flow_out`, signalType: 'DONE' } }
        ]
      },
      interfaces,
      ports,
      actions: [
        {
          actionId: `${n.id}_action`,
          actionType: n.data.kind === 'device' ? 'EMIT_SIGNAL' : 'EXECUTE_LOGIC',
          interfaceRef: n.data.kind === 'device' ? 'if_wf_cmd_in' : `${n.id}_flow_out`,
          signalType: n.data.kind === 'device' ? n.data.functionId || 'EXEC' : n.data.kind.toUpperCase(),
          logicExpression: n.data.kind === 'device' ? undefined : n.data.kind.toUpperCase(),
          payload: n.data.kind === 'device'
            ? { specVersion: 'smartlab.signal.v1', modelId: n.data.modelId, capabilityRef: n.data.functionId, commandId: n.data.functionId, parameters: n.data.parameters }
            : { conditions: n.data.conditions || [] }
        }
      ],
      _order: idx
    }
  })

  const interfaceConnections = edges.value
    .filter((e: any) => String(e.sourceHandle || '').startsWith('if:') && String(e.targetHandle || '').startsWith('if:'))
    .map((e: any, idx: number) => ({
      connectionId: `conn_if_${idx + 1}`,
      connectionType: 'NODE_TO_NODE',
      source: { interfaceRef: `${e.source}_${String(e.sourceHandle).replace('if:', '')}` },
      target: { interfaceRef: `${e.target}_${String(e.targetHandle).replace('if:', '')}` }
    }))

  const portConnections = edges.value
    .filter((e: any) => String(e.sourceHandle || '').startsWith('port:') && String(e.targetHandle || '').startsWith('port:'))
    .map((e: any, idx: number) => ({
      connectionId: `conn_port_${idx + 1}`,
      source: { nodeRef: e.source, portRef: `${e.source}_${String(e.sourceHandle).replace('port:', '')}` },
      target: { nodeRef: e.target, portRef: `${e.target}_${String(e.targetHandle).replace('port:', '')}` }
    }))

  return { templateId: selectedWorkflowId.value || '', templateName: workflowName.value, nodesDef, interfaceConnections, portConnections }
}

const saveWorkflow = async () => {
  if (!workflowName.value.trim()) return ElMessage.warning('请输入流程名称')
  if (nodes.value.length === 0) return ElMessage.warning('请先添加节点')

  saving.value = true
  try {
    const payload = buildPayload()
    const res = await axios.post('/api/workflow/save', payload)
    if (res.data?.success) {
      ElMessage.success('流程保存成功')
      await fetchWorkflows()
    } else {
      ElMessage.error(res.data?.message || '流程保存失败')
    }
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '流程保存失败')
  } finally {
    saving.value = false
  }
}

const decodeRef = (refText: string, nodeIds: string[]) => {
  const matchedNodeId = nodeIds.find(id => refText.startsWith(`${id}_`))
  if (!matchedNodeId) return null
  return { nodeId: matchedNodeId, localRef: refText.replace(`${matchedNodeId}_`, '') }
}

const loadWorkflow = async (templateId: string) => {
  selectedWorkflowId.value = templateId
  try {
    const res = await axios.get(`/api/workflow/detail/${templateId}`)
    if (!res.data?.success || !res.data?.data) return ElMessage.error(res.data?.message || '加载流程失败')

    const tpl = res.data.data
    if ((tpl.nodesDef || []).some((n: any) => ['LLM_NODE', 'LLM'].includes(String(n.capability?.functionType || '').toUpperCase()))) {
      return ElMessage.error('该旧流程包含下层 LLM 节点，已禁止加载。请在上层编排中调用确定性 SmartLab 工作流。')
    }
    workflowName.value = tpl.templateName || tpl.name || ''

    const loadedNodes = (tpl.nodesDef || []).map((n: any, idx: number) => {
      const capability = n.capability || {}
      const functionType = capability.functionType || ''
      const kind: FlowNodeKind =
        n.nodeType === 'DEVICE_CAPABILITY_NODE' ? 'device'
        : functionType === 'START' ? 'start'
        : functionType === 'END' ? 'end'
        : functionType === 'BRANCH' ? 'branch'
        : 'join'

      const rawInterfaces = (n.interfaces || []).map((it: any) => ({
        interfaceId: String(it.interfaceId || '').replace(`${n.nodeId}_`, ''),
        direction: (it.direction || 'IN') as 'IN' | 'OUT',
        signalType: (it.signalType || 'DONE') as NodeInterface['signalType'],
        triggerMode: (it.triggerMode || 'ALWAYS') as NodeInterface['triggerMode'],
        triggerExpr: it.triggerExpr || ''
      }))
      const rawPorts = (n.ports || []).map((p: any) => ({
        portId: String(p.portId || '').replace(`${n.nodeId}_`, ''),
        direction: (p.direction || 'IN') as 'IN' | 'OUT',
        dataType: (p.dataType || 'string') as NodePort['dataType'],
        variableBinding: p.internalVariableBinding || ''
      }))
      const rawVariables = (n.internalVariables || []).map((v: any) => ({ name: v.name || '', dataType: (v.dataType || 'string') as NodeVariable['dataType'], mapping: v.mapping || '' }))

      return {
        id: n.nodeId,
        type: 'custom',
        position: { x: 120 + (idx % 4) * 260, y: 80 + Math.floor(idx / 4) * 150 },
        data: {
          kind,
          name: n.name || kindLabel(kind),
          nodeType: n.nodeType || (kind === 'device' ? 'DEVICE_CAPABILITY_NODE' : 'FUNCTIONAL_NODE'),
          modelId: capability.deviceModelRef || '',
          functionId: capability.capabilityRef || '',
          parameters: n.actions?.[0]?.payload?.parameters || {},
          conditions: n.actions?.[0]?.payload?.conditions || [],
          interfaces: rawInterfaces.length ? rawInterfaces : defaultInterfacesByKind(kind),
          ports: rawPorts.length ? rawPorts : defaultPortsByKind(kind),
          internalVariables: rawVariables.length ? rawVariables : defaultVariablesByKind(kind)
        }
      }
    })

    nodes.value = loadedNodes
    const nodeIds = loadedNodes.map((n: any) => n.id)

    const ifEdges = (tpl.interfaceConnections || []).map((conn: any, idx: number) => {
      const src = decodeRef(conn.source?.interfaceRef || '', nodeIds)
      const tgt = decodeRef(conn.target?.interfaceRef || '', nodeIds)
      if (!src || !tgt) return null
      return { id: `edge_if_${idx}`, source: src.nodeId, sourceHandle: `if:${src.localRef}`, target: tgt.nodeId, targetHandle: `if:${tgt.localRef}`, type: 'smoothstep', animated: true, style: { stroke: '#6b7280', strokeWidth: 2 }, markerEnd: MarkerType.ArrowClosed }
    }).filter(Boolean)

    const portEdges = (tpl.portConnections || []).map((conn: any, idx: number) => {
      const sourceRef = conn.source?.portRef || conn.sourcePort || conn.fromPort || ''
      const targetRef = conn.target?.portRef || conn.targetPort || conn.toPort || ''
      const src = decodeRef(sourceRef, nodeIds)
      const tgt = decodeRef(targetRef, nodeIds)
      if (!src || !tgt) return null
      return { id: `edge_port_${idx}`, source: src.nodeId, sourceHandle: `port:${src.localRef}`, target: tgt.nodeId, targetHandle: `port:${tgt.localRef}`, type: 'smoothstep', animated: false, style: { stroke: '#8f6d2a', strokeWidth: 2, strokeDasharray: '5,4' }, markerEnd: MarkerType.ArrowClosed }
    }).filter(Boolean)

    edges.value = [...ifEdges, ...portEdges]
    drawerVisible.value = false
    selectedNodeId.value = ''
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '加载流程失败')
  }
}

const fetchBaseData = async () => {
  loadingBase.value = true
  try {
    const res = await axios.get('/api/device/model/list')
    if (res.data?.success) deviceModels.value = res.data.data || []
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '设备模型加载失败')
  } finally {
    loadingBase.value = false
  }
}
const fetchWorkflows = async () => {
  loadingWorkflows.value = true
  try {
    const res = await axios.get('/api/workflow/list')
    if (res.data?.success) workflows.value = res.data.data || []
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '流程列表加载失败')
  } finally {
    loadingWorkflows.value = false
  }
}

onMounted(async () => {
  await Promise.all([fetchBaseData(), fetchWorkflows()])
})
</script>

<style scoped>
.workflow-designer {
  --bg-main: #f8fafc;
  --bg-panel: rgba(255, 255, 255, 0.85);
  --bg-node: rgba(255, 255, 255, 0.95);
  --line: rgba(0, 0, 0, 0.08);
  --line-strong: rgba(0, 0, 0, 0.15);
  --text-main: #0f172a;
  --text-sub: #64748b;
  --accent: #3b82f6;
  
  display: flex;
  height: calc(100vh - 52px);
  background: var(--bg-main);
  color: var(--text-main);
  font-family: "Inter", "PingFang SC", sans-serif;
}

.sidebar { 
  width: 320px; 
  background: var(--bg-panel); 
  backdrop-filter: blur(20px);
  border-right: 1px solid var(--line);
  box-shadow: 4px 0 24px rgba(0,0,0,0.03);
  z-index: 10;
}

.sidebar-tabs { height: 100%; }
.sidebar-tabs :deep(.el-tabs__header) { margin: 0; border-bottom: 1px solid var(--line); }
.sidebar-tabs :deep(.el-tabs__item) { color: var(--text-sub); }
.sidebar-tabs :deep(.el-tabs__item.is-active) { color: var(--text-main); font-weight: 600; }
.sidebar-tabs :deep(.el-tabs__active-bar) { background: var(--accent); }
.sidebar-tabs :deep(.el-tabs__nav-wrap::after) { display: none; }

.sidebar-scroll { height: calc(100vh - 120px); overflow: auto; padding: 20px; }
.sidebar-scroll::-webkit-scrollbar { width: 6px; }
.sidebar-scroll::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.1); border-radius: 4px; }

.group-title { font-size: 13px; font-weight: 700; color: #94a3b8; margin: 12px 0 12px; letter-spacing: 0.05em; text-transform: uppercase; }

.logic-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; margin-bottom: 20px; }
.logic-item { 
  display: flex; align-items: center; justify-content: center; gap: 8px; 
  padding: 12px 10px; 
  border: 1px solid rgba(0,0,0,0.08); 
  border-radius: 10px; 
  font-size: 13px; font-weight: 500;
  color: var(--text-main); 
  background: #ffffff; 
  cursor: grab; 
  transition: all 0.2s ease;
  box-shadow: 0 2px 6px rgba(0,0,0,0.02);
}
.logic-item:hover {
  background: #eff6ff;
  border-color: rgba(59,130,246,0.3);
  transform: translateY(-2px);
  box-shadow: 0 4px 12px rgba(59,130,246,0.1);
}

.palette-list { display: flex; flex-direction: column; gap: 12px; margin-bottom: 20px; }
.palette-item, .workflow-item { 
  border: 1px solid var(--line); 
  border-radius: 12px; 
  background: #ffffff; 
  padding: 14px; 
  display: flex; align-items: center; gap: 12px; 
  cursor: pointer; 
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(0,0,0,0.02);
}
.palette-item:hover, .workflow-item:hover {
  background: #f8fafc;
  border-color: var(--line-strong);
  box-shadow: 0 4px 12px rgba(0,0,0,0.04);
}
.workflow-item { display: block; margin-bottom: 10px; }
.workflow-item.active { 
  border-color: var(--accent); 
  background: #eff6ff; 
  box-shadow: inset 0 0 0 1px var(--accent), 0 2px 8px rgba(59,130,246,0.1);
}

.item-title { color: var(--text-main); font-size: 14px; font-weight: 600; }
.item-sub { color: var(--text-sub); font-size: 12px; margin-top: 4px; }
.mono { font-family: 'JetBrains Mono', Consolas, monospace; }

.main { flex: 1; min-width: 0; display: flex; flex-direction: column; position: relative; }

/* Floating Header with Blur */
.header { 
  position: absolute;
  top: 20px; left: 20px; right: 20px;
  z-index: 20;
  min-height: 64px; 
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(0,0,0,0.05);
  border-radius: 16px;
  display: flex; align-items: center; justify-content: space-between; 
  padding: 12px 20px; 
  box-shadow: 0 8px 32px rgba(0,0,0,0.04);
}

.header-left { display: flex; flex-direction: column; gap: 8px; min-width: 0; }
.name-input { width: min(420px, 42vw); }
.name-input :deep(.el-input__wrapper) {
  background-color: #ffffff;
  border: 1px solid rgba(0,0,0,0.1);
  box-shadow: 0 2px 6px rgba(0,0,0,0.02);
  color: #0f172a;
}
.name-input :deep(.el-input__inner) { color: #0f172a; font-weight: 600; }

.protocol-strip { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; color: #64748b; font-size: 12px; }
.protocol-strip span { 
  border: 1px solid rgba(0,0,0,0.08); 
  background: #f1f5f9; 
  border-radius: 6px; 
  padding: 4px 8px; 
  white-space: nowrap; 
}
.header-actions { display: flex; gap: 12px; }
.header-actions .el-button {
  border-radius: 8px;
  font-weight: 600;
  backdrop-filter: blur(4px);
}

.canvas-wrap { flex: 1; min-height: 0; width: 100%; height: 100%; position: absolute; inset: 0; }
.canvas-wrap :deep(.vue-flow__pane) { 
  background-image: 
    linear-gradient(rgba(0,0,0,0.05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(0,0,0,0.05) 1px, transparent 1px);
  background-size: 24px 24px;
}

/* Premium Glass Node */
.node-shell { 
  width: 240px; min-height: 90px; 
  border: 1px solid rgba(0,0,0,0.1); 
  border-radius: 14px; 
  background: var(--bg-node); 
  backdrop-filter: blur(12px);
  padding: 14px; box-sizing: border-box; position: relative; 
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.05), inset 0 1px 0 #ffffff; 
  transition: all 0.25s cubic-bezier(0.4, 0, 0.2, 1);
}
.node-shell:hover {
  transform: translateY(-4px) scale(1.02);
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.08), inset 0 1px 0 #ffffff;
  border-color: rgba(0,0,0,0.15);
}
.vue-flow__node-custom.selected .node-shell {
  border-color: var(--accent);
  box-shadow: 0 0 0 2px rgba(59,130,246,0.3), 0 16px 40px rgba(0, 0, 0, 0.08);
}
.node-shell.kind-llm { border-style: dashed; background: rgba(248, 250, 252, 0.9); }

.node-head { display: flex; align-items: center; gap: 10px; margin-bottom: 10px; }
.node-tag { 
  display: inline-flex; align-items: center; height: 22px; padding: 0 10px; 
  border-radius: 12px; font-size: 11px; font-weight: 700;
  background: #eff6ff; color: #2563eb; 
}
.node-title { font-size: 14px; font-weight: 700; color: #0f172a; }
.node-sub { margin-top: 6px; font-size: 12px; color: var(--text-sub); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.node-meta { margin-top: 8px; font-size: 11px; color: #64748b; display: flex; gap: 8px; flex-wrap: wrap; }
.node-meta span { background: #f1f5f9; padding: 2px 6px; border-radius: 4px; border: 1px solid #e2e8f0; }

/* Glowing Edges & Handles */
.node-handle { 
  width: 12px !important; height: 12px !important; 
  border: 2px solid #94a3b8 !important; 
  background: #ffffff !important; 
  transition: all 0.2s;
}
.node-handle:hover {
  transform: scale(1.5);
  background: var(--accent) !important;
  border-color: #fff !important;
  box-shadow: 0 0 10px rgba(59,130,246,0.4);
}
.flow-handle { border-radius: 50% !important; }
.data-handle { border-radius: 3px !important; border-color: #8b5cf6 !important; }

/* Custom Drawer styling */
:deep(.el-drawer) {
  background: #ffffff;
  color: #0f172a;
}
:deep(.el-drawer__header) { color: #0f172a; border-bottom: 1px solid rgba(0,0,0,0.05); margin-bottom: 0; padding-bottom: 16px; }

.drawer-content { padding: 10px 4px; }
.config-tabs :deep(.el-tabs__item) { font-size: 14px; color: #64748b; }
.config-tabs :deep(.el-tabs__item.is-active) { color: #0f172a; font-weight: 600; }

.table-actions { display: flex; justify-content: flex-end; margin-bottom: 12px; }
.condition-list { display: flex; flex-direction: column; gap: 12px; width: 100%; }
.condition-row { display: flex; align-items: center; gap: 10px; }
.condition-label { width: 80px; color: var(--text-sub); font-size: 13px; }
.hint { margin-top: 10px; color: #64748b; font-size: 12px; }

.signal-preview { 
  width: 100%; border: 1px solid rgba(0,0,0,0.05); 
  border-radius: 8px; background: #f8fafc; 
  padding: 12px; display: grid; grid-template-columns: 1fr 1fr; gap: 8px 12px; 
}
.signal-preview div { display: flex; justify-content: space-between; gap: 10px; font-size: 13px; min-width: 0; }
.signal-preview span { color: #64748b; white-space: nowrap; }
.signal-preview b { color: #0f172a; font-family: 'JetBrains Mono', Consolas, monospace; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

/* Vue Flow Edge animation glow */
:deep(.vue-flow__edge-path) {
  stroke: rgba(148, 163, 184, 0.8);
}
:deep(.vue-flow__edge.selected .vue-flow__edge-path) {
  stroke: var(--accent);
  filter: drop-shadow(0 0 4px rgba(59,130,246,0.3));
}

@media (max-width: 920px) {
  .workflow-designer { flex-direction: column; }
  .sidebar { width: 100%; height: 260px; border-right: 0; border-bottom: 1px solid var(--line); }
  .sidebar-scroll { height: 210px; }
  .header { position: relative; top: 0; left: 0; right: 0; border-radius: 0; box-shadow: none; align-items: stretch; flex-direction: column; }
  .header-actions { justify-content: flex-end; }
  .name-input { width: 100%; }
  .canvas-wrap { position: relative; }
}
</style>

````

---

## Frontend/vite.config.js

````text
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})

````
