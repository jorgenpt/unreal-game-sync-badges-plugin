package io.jenkins.plugins.ugs;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import net.sf.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.routing.HttpRoutePlanner;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Workflow step to post a UGS badge
 */
public class PostUGSBadgeStep extends Step {
    private static final Logger logger = Logger.getLogger(PostUGSBadgeStep.class.getName());

    private String apiUrl;
    private String credentialId;

    private String project;
    private int changelist;
    private BadgeResult result;
    private String name;
    private String url;
    private boolean failOnError;

    @DataBoundConstructor
    public PostUGSBadgeStep(String project, int changelist, BadgeResult result, String name, String url) {
        this.project = project;
        this.changelist = changelist;
        this.result = result;
        this.name = name;
        this.url = url;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    @DataBoundSetter
    public void setApiUrl(String apiUrl) {
        this.apiUrl = Util.fixEmpty(apiUrl);
    }

    public String getCredentialId() {
        return credentialId;
    }

    @DataBoundSetter
    public void setCredentialId(String credentialId) {
        this.credentialId = Util.fixEmpty(credentialId);
    }

    public String getProject() {
        return project;
    }

    @DataBoundSetter
    public void setProject(String project) {
        this.project = Util.fixEmpty(project);
    }

    public int getChangelist() {
        return changelist;
    }

    @DataBoundSetter
    public void setChangelist(int changelist) {
        this.changelist = changelist;
    }

    public BadgeResult getResult() {
        return result;
    }

    @DataBoundSetter
    public void setResult(BadgeResult result) {
        this.result = result;
    }

    public String getName() {
        return name;
    }

    @DataBoundSetter
    public void setName(String name) {
        this.name = Util.fixEmpty(name);
    }

    public String getUrl() {
        return url;
    }

    @DataBoundSetter
    public void setUrl(String url) {
        this.url = Util.fixEmpty(url);
    }

    public boolean isFailOnError() {
        return failOnError;
    }

    @DataBoundSetter
    public void setFailOnError(boolean failOnError) {
        this.failOnError = failOnError;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new PostUGSBadgeStepExecution(this, context);
    }

    public static class PostUGSBadgeStepExecution extends SynchronousNonBlockingStepExecution<Boolean> {

        private static final long serialVersionUID = 1L;

        private transient final PostUGSBadgeStep step;

        PostUGSBadgeStepExecution(PostUGSBadgeStep step, StepContext context) {
            super(context);
            this.step = step;
        }

        @Override
        protected Boolean run() throws Exception {

            Jenkins jenkins = Jenkins.get();
            DescriptorImpl desc = jenkins.getDescriptorByType(DescriptorImpl.class);

            String apiUrl = step.apiUrl != null ? step.getApiUrl() : desc.getApiUrl();
            if (apiUrl == null) {
                throw new IllegalArgumentException("Neither global config nor step config specifies apiUrl");
            }

            if (step.getName() == null) {
                throw new IllegalArgumentException("No badge name specified");
            }

            if (step.getResult() == null) {
                throw new IllegalArgumentException("No badge result specified");
            }

            if (step.getUrl() == null) {
                throw new IllegalArgumentException("No badge URL specified");
            }

            if (step.getProject() == null) {
                throw new IllegalArgumentException("No badge project specified");
            }

            String credentialId = step.credentialId != null ? step.getCredentialId() : desc.getCredentialId();

            TaskListener listener = getContext().get(TaskListener.class);
            Objects.requireNonNull(listener, "Listener is mandatory here");

            String endpointUrl = apiUrl + (!apiUrl.endsWith("/") ? "/" : "") + "api/build";

            StringCredentials credential = null;
            if (credentialId != null) {
                credential = com.cloudbees.plugins.credentials.CredentialsProvider.findCredentialById(credentialId,
                        StringCredentials.class, getContext().get(Run.class));
                if (credential == null) {
                    throw new IllegalArgumentException(
                            String.format("Could not find a credential with id %s", credentialId));
                }
            }

            listener.getLogger().println(Messages.postUGSBadgeStepValues(endpointUrl.toString(), step.getChangelist(),
                    step.getName(), step.getResult().toString(), step.getUrl(), step.getProject()));

            JSONObject body = new JSONObject();
            body.put("ChangeNumber", step.getChangelist());
            body.put("BuildType", step.getName());
            body.put("Result", step.getResult().getUGSValue());
            body.put("Url", step.getUrl());
            body.put("Project", step.getProject());

            try (CloseableHttpClient client = getHttpClient()) {
                HttpPost post = new HttpPost(endpointUrl);

                if (credential != null) {
                    listener.getLogger().println(Messages.authedPost(credentialId));
                    String userInfo = credential.getSecret().getPlainText();
                    String authorizationBase64 = java.util.Base64.getEncoder().encodeToString(userInfo.getBytes("UTF-8"));
                    post.setHeader("Authorization", "Basic " + authorizationBase64);
                } else {
                    listener.getLogger().println(Messages.authlessPost());
                }

                post.setHeader("Content-Type", "application/json; charset=utf-8");
                post.setEntity(new StringEntity(body.toString(), StandardCharsets.UTF_8));

                try (CloseableHttpResponse response = client.execute(post)) {
                    int responseCode = response.getStatusLine().getStatusCode();
                    HttpEntity entity = response.getEntity();
                    String responseString = entity != null ? EntityUtils.toString(entity) : "(null)";
                    if (responseCode != HttpStatus.SC_OK) {
                        logger.log(Level.SEVERE, "Posting to UGS metadata server failed. Response: " + responseString);
                        logger.log(Level.SEVERE, "Response Code: " + responseCode);
                        if (step.failOnError) {
                            if (responseString != null && !responseString.isEmpty()) {
                                throw new AbortException(
                                        Messages.postFailedWithStatusAndResponse(responseCode, responseString));
                            }
                            throw new AbortException(Messages.postFailedWithStatus(responseCode));
                        } else {
                            if (responseString != null) {
                                listener.error(Messages.postFailedWithException(responseString));
                            }
                            listener.error(Messages.postFailed());
                            return false;
                        }
                    } else {
                        logger.fine("Posting succeeded");
                    }
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Error posting to UGS metadata server", e);
                    if (step.isFailOnError()) {
                        throw new AbortException(Messages.postFailedWithException(e));
                    } else {
                        listener.error(Messages.postFailedWithException(e));
                        return false;
                    }
                } finally {
                    post.releaseConnection();
                }
            } catch (IOException e) {
                logger.log(Level.WARNING, "Error closing HttpClient", e);
            }
            return true;
        }

        protected CloseableHttpClient getHttpClient() {
            Jenkins jenkins = Jenkins.getInstanceOrNull();
            ProxyConfiguration proxy = jenkins != null ? jenkins.proxy : null;

            int timeoutInSeconds = 60;

            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(timeoutInSeconds * 1000)
                    .setConnectionRequestTimeout(timeoutInSeconds * 1000)
                    .setSocketTimeout(timeoutInSeconds * 1000).build();

            final HttpClientBuilder clientBuilder = HttpClients
                    .custom()
                    .useSystemProperties()
                    .setDefaultRequestConfig(config);
            final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            clientBuilder.setDefaultCredentialsProvider(credentialsProvider);

            if (proxy != null) {
                final HttpHost proxyHost = new HttpHost(proxy.name, proxy.port);
                final HttpRoutePlanner routePlanner = new NoProxyHostCheckerRoutePlanner(proxy.getNoProxyHost(),
                        proxyHost);
                clientBuilder.setRoutePlanner(routePlanner);

                String username = proxy.getUserName();
                String password = proxy.getSecretPassword().getPlainText();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    Credentials credentials;
                    if (username.indexOf('\\') >= 0) {
                        final String domain = username.substring(0, username.indexOf('\\'));
                        final String user = username.substring(username.indexOf('\\') + 1);
                        credentials = new NTCredentials(user, password, "", domain);
                    } else {
                        credentials = new UsernamePasswordCredentials(username, password);
                    }
                    credentialsProvider.setCredentials(new AuthScope(proxyHost), credentials);
                }
            }
            return clientBuilder.build();
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        private String apiUrl;
        private String credentialId;

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(Run.class, TaskListener.class);
        }

        public DescriptorImpl() {
            load();
        }

        public String getApiUrl() {
            return apiUrl;
        }

        @DataBoundSetter
        public void setApiUrl(String apiUrl) {
            this.apiUrl = apiUrl;
        }

        public String getCredentialId() {
            return credentialId;
        }

        @DataBoundSetter
        public void setCredentialId(String credentialId) {
            this.credentialId = Util.fixEmpty(credentialId);
        }

        @NonNull
        @Override
        public String getFunctionName() {
            return "postUGSBadge";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.postUGSBadgeStepDisplayName();
        }

        // Called to populate the credential list in the configuration screens
        public ListBoxModel doFillCredentialIdItems(@AncestorInPath Item context) {
            return findCredentialIdItems(context);
        }

        @Restricted(NoExternalUse.class)
        public static ListBoxModel findCredentialIdItems(@AncestorInPath Item context) {
            Jenkins jenkins = Jenkins.get();

            if (context == null && !jenkins.hasPermission(Jenkins.ADMINISTER) ||
                    context != null && !context.hasPermission(Item.EXTENDED_READ)) {
                return new StandardListBoxModel();
            }

            return new StandardListBoxModel()
                    .includeEmptyValue()
                    .includeAs(ACL.SYSTEM, context, StringCredentials.class);
        }

        @Override
        public boolean configure(StaplerRequest req, JSONObject formData) {
            req.bindJSON(this, formData);
            save();
            return true;
        }
    }
}
