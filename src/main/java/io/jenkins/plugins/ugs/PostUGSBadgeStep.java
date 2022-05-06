package io.jenkins.plugins.ugs;

import com.google.common.collect.ImmutableSet;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.Extension;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.TaskListener;
import java.util.Objects;
import java.util.Set;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
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

import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundSetter;

// import static jenkins.plugins.slack.CredentialsObtainer.getItemForCredentials;
// import static jenkins.plugins.slack.SlackNotifier.DescriptorImpl.findTokenCredentialIdItems;

/**
 * Workflow step to post a UGS badge
 */
public class PostUGSBadgeStep extends Step {

    private static final Logger logger = Logger.getLogger(PostUGSBadgeStep.class.getName());

    private String apiUrl;
    private String project;
    private int changelist;
    private BadgeResult result;
    private String name;
    private String url;
    private Boolean failOnError;

    public String getApiUrl() {
        return apiUrl;
    }
    
    @DataBoundSetter
    public void setApiUrl(String apiUrl)
    {
        this.apiUrl = Util.fixEmpty(apiUrl);
    }
    
    public String getProject() {
        return project;
    }
    
    @DataBoundSetter
    public void setProject(String project)
    {
        this.project = Util.fixEmpty(project);
    }
    
    public int getChangelist() {
        return changelist;
    }
    
    @DataBoundSetter
    public void setChangelist(int changelist)
    {
        this.changelist = changelist;
    }
    
    public BadgeResult getResult() {
        return result;
    }
    
    @DataBoundSetter
    public void setResult(BadgeResult result)
    {
        this.result = result;
    }
    
    public String getName() {
        return name;
    }
    
    @DataBoundSetter
    public void setName(String name)
    {
        this.name = Util.fixEmpty(name);
    }
    
    public String getUrl() {
        return url;
    }
    
    @DataBoundSetter
    public void setUrl(String url)
    {
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
            // Item item = getItemForCredentials(getContext());
            DescriptorImpl desc = jenkins.getDescriptorByType(DescriptorImpl.class);

            URL apiUrl = new URL(step.apiUrl != null ? step.apiUrl : desc.getApiUrl());
            String userInfo = apiUrl.getUserInfo();

            String file = apiUrl.getPath();
            if (!file.endsWith("/"))
            {
                file += "/";
            }
            file += "api/build";
            if (apiUrl.getQuery() != null)
            {
                file += apiUrl.getQuery();
            }

            URL endpointUrl = new URL(apiUrl.getProtocol(), apiUrl.getHost(), apiUrl.getPort(), file);
        
            TaskListener listener = getContext().get(TaskListener.class);
            Objects.requireNonNull(listener, "Listener is mandatory here");

            // listener.getLogger().println(Messages.slackSendStepValues(
            //         defaultIfEmpty(baseUrl), defaultIfEmpty(teamDomain), channel, defaultIfEmpty(color), botUser,
            //         defaultIfEmpty(tokenCredentialId), notifyCommitters, defaultIfEmpty(iconEmoji), defaultIfEmpty(username), defaultIfEmpty(step.timestamp))
            // );
            // final String populatedToken;
            // try {
            //     populatedToken = CredentialsObtainer.getTokenToUse(tokenCredentialId, item, token);
            // } catch (IllegalArgumentException e) {
            //     listener.error(Messages
            //             .notificationFailedWithException(e));
            //     return null;
            // }

            JSONObject body = new JSONObject();
            body.put("ChangeNumber", step.getChangelist());
            body.put("BuildType", step.getName());
            body.put("Result", step.getResult().toString());
            body.put("Url", step.getUrl());
            body.put("Project", step.getProject());

            try (CloseableHttpClient client = getHttpClient()) {
                HttpPost post = new HttpPost(endpointUrl.toURI());

                if (userInfo != null)
                {
                    String authorizationBase64 = java.util.Base64.getEncoder().encodeToString(userInfo.getBytes());
                    post.setHeader("Authorization", "Basic " + authorizationBase64);
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
                            if (responseString != null) {
                                throw new AbortException(Messages.postFailedWithException(responseString));
                            }
                            throw new AbortException(Messages.postFailed());
                        }
                        else
                        {
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
                    if (step.isFailOnError())
                    {
                        throw new AbortException(Messages.postFailedWithException(e));
                    }
                    else
                    {
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
                final HttpRoutePlanner routePlanner = new NoProxyHostCheckerRoutePlanner(proxy.getNoProxyHost(), proxyHost);
                clientBuilder.setRoutePlanner(routePlanner);
    
                String username = proxy.getUserName();
                String password = proxy.getSecretPassword().getPlainText();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    Credentials credentials;
                    if (username.indexOf('\\') >= 0){
                        final String domain = username.substring(0, username.indexOf('\\'));
                        final String user = username.substring(username.indexOf('\\') + 1);
                        credentials = new NTCredentials(user, password, "", domain);
                    } else {
                        credentials = new UsernamePasswordCredentials(username, password);
                    }
                    credentialsProvider.setCredentials(new AuthScope(proxyHost),credentials);
                }
            }
            return clientBuilder.build();
        }
    }

    
    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        public static final String PLUGIN_DISPLAY_NAME = "UGS Badge";
        private String apiUrl;

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return ImmutableSet.of(TaskListener.class);
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

        @NonNull
        @Override
        public String getFunctionName() {
            return "postUGSBadge";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return PLUGIN_DISPLAY_NAME;
        }
    }
}
