package com.kbpack.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "kbpack")
public class KbpackProperties {

    private final Preview preview = new Preview();
    private final Task task = new Task();
    private final Upload upload = new Upload();
    private final Parser parser = new Parser();
    private final Storage storage = new Storage();
    private final Search search = new Search();
    private final Init init = new Init();
    private String appBaseUrl = "http://localhost:5173";
    private String previewBaseUrl = "http://localhost:18080";

    public Preview getPreview() {
        return preview;
    }

    public Task getTask() {
        return task;
    }

    public Upload getUpload() {
        return upload;
    }

    public Parser getParser() { return parser; }

    public Storage getStorage() {
        return storage;
    }

    public Search getSearch() {
        return search;
    }

    public Init getInit() {
        return init;
    }

    public String getAppBaseUrl() {
        return appBaseUrl;
    }

    public void setAppBaseUrl(String appBaseUrl) {
        this.appBaseUrl = appBaseUrl;
    }

    public String getPreviewBaseUrl() {
        return previewBaseUrl;
    }

    public void setPreviewBaseUrl(String previewBaseUrl) {
        this.previewBaseUrl = previewBaseUrl;
    }

    public static class Preview {
        private String host = "kb-preview.nas.local";
        private String ticketSecret = "change-me";
        private long ticketTtlSeconds = 60;
        private long sessionTtlSeconds = 1800;
        private boolean enforceHost = true;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public String getTicketSecret() {
            return ticketSecret;
        }

        public void setTicketSecret(String ticketSecret) {
            this.ticketSecret = ticketSecret;
        }

        public long getTicketTtlSeconds() {
            return ticketTtlSeconds;
        }

        public void setTicketTtlSeconds(long ticketTtlSeconds) {
            this.ticketTtlSeconds = ticketTtlSeconds;
        }

        public long getSessionTtlSeconds() {
            return sessionTtlSeconds;
        }

        public void setSessionTtlSeconds(long sessionTtlSeconds) {
            this.sessionTtlSeconds = sessionTtlSeconds;
        }

        public boolean isEnforceHost() { return enforceHost; }
        public void setEnforceHost(boolean enforceHost) { this.enforceHost = enforceHost; }
    }

    public static class Task {
        private long pollIntervalMs = 3000;
        private int threadPoolSize = 2;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getThreadPoolSize() {
            return threadPoolSize;
        }

        public void setThreadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
        }
    }

    public static class Upload {
        private long maxPackageSizeBytes = 500L * 1024 * 1024;
        private long maxUnpackedSizeBytes = 2L * 1024 * 1024 * 1024;
        private int maxFileCount = 20_000;
        private long maxSingleFileSizeBytes = 200L * 1024 * 1024;
        private int maxPathLength = 512;

        public long getMaxPackageSizeBytes() { return maxPackageSizeBytes; }
        public void setMaxPackageSizeBytes(long value) { this.maxPackageSizeBytes = value; }
        public long getMaxUnpackedSizeBytes() { return maxUnpackedSizeBytes; }
        public void setMaxUnpackedSizeBytes(long value) { this.maxUnpackedSizeBytes = value; }
        public int getMaxFileCount() { return maxFileCount; }
        public void setMaxFileCount(int value) { this.maxFileCount = value; }
        public long getMaxSingleFileSizeBytes() { return maxSingleFileSizeBytes; }
        public void setMaxSingleFileSizeBytes(long value) { this.maxSingleFileSizeBytes = value; }
        public int getMaxPathLength() { return maxPathLength; }
        public void setMaxPathLength(int value) { this.maxPathLength = value; }
    }

    public static class Parser {
        private long maxTextFileBytes = 32L * 1024 * 1024;
        private long maxInMemoryBytes = 256L * 1024 * 1024;
        public long getMaxTextFileBytes() { return maxTextFileBytes; }
        public void setMaxTextFileBytes(long value) { this.maxTextFileBytes = value; }
        public long getMaxInMemoryBytes() { return maxInMemoryBytes; }
        public void setMaxInMemoryBytes(long value) { this.maxInMemoryBytes = value; }
    }

    public static class Storage {
        private final Minio minio = new Minio();

        public Minio getMinio() {
            return minio;
        }

        public static class Minio {
            private String endpoint = "http://localhost:9000";
            private String accessKey = "minioadmin";
            private String secretKey = "minioadmin";
            private final Buckets buckets = new Buckets();

            public String getEndpoint() {
                return endpoint;
            }

            public void setEndpoint(String endpoint) {
                this.endpoint = endpoint;
            }

            public String getAccessKey() {
                return accessKey;
            }

            public void setAccessKey(String accessKey) {
                this.accessKey = accessKey;
            }

            public String getSecretKey() {
                return secretKey;
            }

            public void setSecretKey(String secretKey) {
                this.secretKey = secretKey;
            }

            public Buckets getBuckets() {
                return buckets;
            }

            public static class Buckets {
                private String original = "kb-original";
                private String packages = "kb-packages";
                private String derived = "kb-derived";
                private String backup = "kb-backup";

                public String getOriginal() {
                    return original;
                }

                public void setOriginal(String original) {
                    this.original = original;
                }

                public String getPackages() {
                    return packages;
                }

                public void setPackages(String packages) {
                    this.packages = packages;
                }

                public String getDerived() {
                    return derived;
                }

                public void setDerived(String derived) {
                    this.derived = derived;
                }

                public String getBackup() {
                    return backup;
                }

                public void setBackup(String backup) {
                    this.backup = backup;
                }

                public String[] all() {
                    return new String[]{original, packages, derived, backup};
                }
            }
        }
    }

    public static class Search {
        private final Meilisearch meilisearch = new Meilisearch();

        public Meilisearch getMeilisearch() {
            return meilisearch;
        }

        public static class Meilisearch {
            private String host = "http://localhost:7700";
            private String apiKey = "change-me";
            private String indexUid = "kb_chunks";

            public String getHost() {
                return host;
            }

            public void setHost(String host) {
                this.host = host;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }

            public String getIndexUid() {
                return indexUid;
            }

            public void setIndexUid(String indexUid) {
                this.indexUid = indexUid;
            }
        }
    }

    public static class Init {
        private String adminUsername = "admin";
        private String adminPassword = "admin123456";

        public String getAdminUsername() {
            return adminUsername;
        }

        public void setAdminUsername(String adminUsername) {
            this.adminUsername = adminUsername;
        }

        public String getAdminPassword() {
            return adminPassword;
        }

        public void setAdminPassword(String adminPassword) {
            this.adminPassword = adminPassword;
        }
    }
}
