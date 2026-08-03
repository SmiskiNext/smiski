-- Extracts tenant and user identity from the Forge Invocation Token (FIT)
-- payload published by the jwt_authn filter as dynamic metadata.
--
-- The jwt_authn provider `forge_fit` is configured with
-- `payload_in_metadata: fit_payload`, so the verified claims are readable
-- under the `envoy.filters.http.jwt_authn` metadata namespace.
--
-- Two request headers are produced for the downstream gateway ext_authz
-- service and the backend services:
--   x-fit-cloud-id    cloudId parsed from the `app.apiBaseUrl` claim
--   x-fit-account-id  accountId taken from the `principal` claim
--
-- Both headers are always cleared first so a client cannot spoof them by
-- sending their own values.

local JWT_METADATA_NAMESPACE = 'envoy.filters.http.jwt_authn'
local PAYLOAD_KEY = 'fit_payload'
local CLOUD_ID_HEADER = 'x-fit-cloud-id'
local ACCOUNT_ID_HEADER = 'x-fit-account-id'

--- Reads the last non-empty path segment of an API base URL.
-- `https://api.atlassian.com/ex/jira/abc123-def456` yields `abc123-def456`.
-- @param api_base_url string|nil the FIT `app.apiBaseUrl` claim
-- @return string|nil the cloudId, or nil when it cannot be determined
local function extract_cloud_id(api_base_url)
    if type(api_base_url) ~= 'string' or api_base_url == '' then
        return nil
    end

    local without_query = api_base_url:match('^([^?#]*)') or api_base_url
    local last_segment = nil

    for segment in without_query:gmatch('[^/]+') do
        last_segment = segment
    end

    if last_segment == nil or last_segment:find('^%a[%w+.-]*:$') ~= nil then
        return nil
    end

    return last_segment
end

--- Reads the FIT `principal` claim as the accountId.
-- @param principal any the raw `principal` claim value
-- @return string|nil the accountId, or nil when absent or not a string
local function extract_account_id(principal)
    if type(principal) ~= 'string' or principal == '' then
        return nil
    end

    return principal
end

--- Reads the verified FIT payload from the jwt_authn dynamic metadata.
-- @param request_handle userdata the Envoy Lua request handle
-- @return table|nil the FIT claims table, or nil when unavailable
local function read_fit_payload(request_handle)
    local ok, metadata = pcall(function()
        return request_handle:streamInfo():dynamicMetadata():get(
            JWT_METADATA_NAMESPACE
        )
    end)

    if not ok or type(metadata) ~= 'table' then
        return nil
    end

    local payload = metadata[PAYLOAD_KEY]
    if type(payload) ~= 'table' then
        return nil
    end

    return payload
end

function envoy_on_request(request_handle)
    local headers = request_handle:headers()

    headers:remove(CLOUD_ID_HEADER)
    headers:remove(ACCOUNT_ID_HEADER)

    local payload = read_fit_payload(request_handle)
    if payload == nil then
        request_handle:logDebug(
            'extract_claims: no FIT payload in dynamic metadata'
        )
        return
    end

    local app = payload['app']
    local api_base_url = type(app) == 'table' and app['apiBaseUrl'] or nil

    local cloud_id = extract_cloud_id(api_base_url)
    if cloud_id ~= nil then
        headers:add(CLOUD_ID_HEADER, cloud_id)
    else
        request_handle:logWarn(
            'extract_claims: unable to derive cloudId from app.apiBaseUrl'
        )
    end

    local account_id = extract_account_id(payload['principal'])
    if account_id ~= nil then
        headers:add(ACCOUNT_ID_HEADER, account_id)
    else
        request_handle:logWarn(
            'extract_claims: missing or invalid principal claim'
        )
    end
end
