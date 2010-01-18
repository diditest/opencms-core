/*
 * File   : $Source: /alkacon/cvs/opencms/src/org/opencms/workplace/editors/sitemap/Attic/CmsSitemapServer.java,v $
 * Date   : $Date: 2010/01/18 14:05:22 $
 * Version: $Revision: 1.28 $
 *
 * This library is part of OpenCms -
 * the Open Source Content Management System
 *
 * Copyright (c) 2002 - 2009 Alkacon Software GmbH (http://www.alkacon.com)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * For further information about Alkacon Software GmbH, please see the
 * company website: http://www.alkacon.com
 *
 * For further information about OpenCms, please see the
 * project website: http://www.opencms.org
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.opencms.workplace.editors.sitemap;

import org.opencms.db.CmsUserSettings;
import org.opencms.file.CmsObject;
import org.opencms.file.CmsProperty;
import org.opencms.file.CmsPropertyDefinition;
import org.opencms.file.CmsResource;
import org.opencms.file.CmsResourceFilter;
import org.opencms.file.CmsUser;
import org.opencms.file.CmsVfsResourceNotFoundException;
import org.opencms.file.history.CmsHistoryResourceHandler;
import org.opencms.file.types.CmsResourceTypeXmlContainerPage;
import org.opencms.file.types.CmsResourceTypeXmlSitemap;
import org.opencms.file.types.I_CmsResourceType;
import org.opencms.i18n.CmsLocaleManager;
import org.opencms.i18n.CmsMessages;
import org.opencms.json.JSONArray;
import org.opencms.json.JSONException;
import org.opencms.json.JSONObject;
import org.opencms.main.CmsException;
import org.opencms.main.CmsLog;
import org.opencms.main.OpenCms;
import org.opencms.relations.CmsRelation;
import org.opencms.relations.CmsRelationFilter;
import org.opencms.relations.CmsRelationType;
import org.opencms.security.CmsPermissionViolationException;
import org.opencms.util.CmsMacroResolver;
import org.opencms.util.CmsUUID;
import org.opencms.workplace.A_CmsAjaxServer;
import org.opencms.xml.CmsXmlContentDefinition;
import org.opencms.xml.content.CmsXmlContentProperty;
import org.opencms.xml.sitemap.CmsSiteEntryBean;
import org.opencms.xml.sitemap.CmsSitemapBean;
import org.opencms.xml.sitemap.CmsSitemapManager;
import org.opencms.xml.sitemap.CmsXmlSitemap;
import org.opencms.xml.sitemap.CmsXmlSitemapFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.PageContext;

import org.apache.commons.logging.Log;

/**
 * Sitemap server used for client/server communication.<p>
 * 
 * see jsp file <tt>/system/workplace/editors/sitemap/server.jsp</tt>.<p>
 * 
 * @author Michael Moossen 
 * 
 * @version $Revision: 1.28 $
 * 
 * @since 7.6
 */
public class CmsSitemapServer extends A_CmsAjaxServer {

    /** Element Property json property constants. */
    public enum JsonProperty {

        /** Property's default value. */
        DEFAULT_VALUE("defaultValue"),
        /** Property's description. */
        DESCRIPTION("description"),
        /** Property's error message. */
        ERROR("error"),
        /** Property's nice name. */
        NICE_NAME("niceName"),
        /** Property's validation regular expression. */
        RULE_REGEX("ruleRegex"),
        /** Property's validation rule type. */
        RULE_TYPE("ruleType"),
        /** Property's type. */
        TYPE("type"),
        /** Property's value. */
        VALUE("value"),
        /** Property's widget. */
        WIDGET("widget"),
        /** Property's widget configuration. */
        WIDGET_CONF("widgetConf");

        /** Property name. */
        private String m_name;

        /** Constructor.<p> */
        private JsonProperty(String name) {

            m_name = name;
        }

        /** 
         * Returns the name.<p>
         * 
         * @return the name
         */
        public String getName() {

            return m_name;
        }
    }

    /** Request parameter action value constants. */
    protected enum Action {
        /** First call to get all the data. */
        ALL,
        /** To fill in the content of a sitemap entry. */
        CONTENT,
        /** To retrieve the favorite or recent list. */
        GET,
        /** To create a new resource as a sitemap entry */
        NEW_ENTRY,
        /** To create a new sitemap */
        NEW_SITEMAP,
        /** To retrieve the sitemap's property definitions. */
        PROPS,
        /** To save the sitemap. */
        SAVE,
        /** To retrieve the favorite or recent list. */
        SET,
        /** To lock the sitemap. */
        STARTEDIT,
        /** To unlock the sitemap. */
        STOPEDIT,
        /** To validate a site entry name. */
        VALIDATE
    }

    /** Json property name constants for request parameters. */
    protected enum JsonRequest {

        /** To retrieve or save the favorites list. */
        FAV("fav"),
        /** The validated/converted site entry name. */
        NAME("name"),
        /** To retrieve or save the recent list. */
        REC("rec"),
        /** Title for a new sitemap */
        TITLE("title"),
        /** The type for which a new entry should be created. */
        TYPE("type");

        /** Property name. */
        private String m_name;

        /** Constructor.<p> */
        private JsonRequest(String name) {

            m_name = name;
        }

        /** 
         * Returns the name.<p>
         * 
         * @return the name
         */
        public String getName() {

            return m_name;
        }
    }

    /** Json property name constants for responses. */
    protected enum JsonResponse {

        /** List of sitemap entries */
        ENTRIES("entries"),
        /** Single sitemap entry */
        ENTRY("entry"),
        /** The favorites list. */
        FAVORITES("favorites"),
        /** id of newly created sitemap */
        ID("id"),
        /** Models of creatable types */
        MODELS("models"),
        /** The validated/converted site entry name. */
        NAME("name"),
        /** The properties configuration. */
        PROPERTIES("properties"),
        /** The recent list. */
        RECENT("recent"),
        /** The sitemap tree. */
        SITEMAP("sitemap"),
        /** Flag to indicate if this is a top-level sitemap or a sub-sitemap. */
        SUB_SITEMAP("subSitemap"),
        /** List of the URIs of sitemaps which reference the current sitemap */
        SUPER_SITEMAPS("superSitemaps"),
        /** Creatable types */
        TYPES("types");

        /** Property name. */
        private String m_name;

        /** Constructor.<p> */
        private JsonResponse(String name) {

            m_name = name;
        }

        /** 
         * Returns the name.<p>
         * 
         * @return the name
         */
        public String getName() {

            return m_name;
        }
    }

    /** Json property name constants for sitemap entries. */
    protected enum JsonSiteEntry {

        /** The HTML content. */
        CONTENT("content"),
        /** The entry id. */
        ID("id"),
        /** The resource id. */
        LINK_ID("linkId"),
        /** The name. */
        NAME("name"),
        /** The properties. */
        PROPERTIES("properties"),
        /** The sub-entries. */
        SUBENTRIES("subentries"),
        /** The title. */
        TITLE("title");

        /** Property name. */
        private String m_name;

        /** Constructor.<p> */
        private JsonSiteEntry(String name) {

            m_name = name;
        }

        /** 
         * Returns the name.<p>
         * 
         * @return the name
         */
        public String getName() {

            return m_name;
        }
    }

    /** Request parameter name constants. */
    protected enum ReqParam {

        /** The action of execute. */
        ACTION("action"),
        /** Generic data parameter. */
        DATA("data"),
        /** The current locale. */
        LOCALE("locale"),
        /** The sitemap uri. */
        SITEMAP("sitemap");

        /** Parameter name. */
        private String m_name;

        /** Constructor.<p> */
        private ReqParam(String name) {

            m_name = name;
        }

        /** 
         * Returns the name.<p>
         * 
         * @return the name
         */
        public String getName() {

            return m_name;
        }
    }

    /** Formatters map key constant. */
    public static final Integer KEY_DEFAULT_FORMATTER = new Integer(-100);

    /** Path constant. */
    public static final String PATH_DEFAULT_FORMATTER = "/system/workplace/editors/sitemap/default-formatter.jsp";

    /** User additional info key constant. */
    protected static final String ADDINFO_SITEMAP_FAVORITE_LIST = "SITEMAP_FAVORITE_LIST";

    /** The log object for this class. */
    private static final Log LOG = CmsLog.getLog(CmsSitemapServer.class);

    /** The site map entry formatters. */
    private Map<Integer, CmsResource> m_formatters;

    /** The session cache. */
    private CmsSitemapSessionCache m_sessionCache;

    /**
     * Constructor.<p>
     * 
     * @param context the JSP page context object
     * @param req the JSP request 
     * @param res the JSP response 
     */
    public CmsSitemapServer(PageContext context, HttpServletRequest req, HttpServletResponse res) {

        super(context, req, res);
        m_sessionCache = (CmsSitemapSessionCache)req.getSession().getAttribute(
            CmsSitemapSessionCache.SESSION_ATTR_SITEMAP_CACHE);
        if (m_sessionCache == null) {
            m_sessionCache = new CmsSitemapSessionCache(getCmsObject());
            req.getSession().setAttribute(CmsSitemapSessionCache.SESSION_ATTR_SITEMAP_CACHE, m_sessionCache);
        }
        if (m_formatters == null) {
            m_formatters = new HashMap<Integer, CmsResource>();
            try {
                m_formatters.put(KEY_DEFAULT_FORMATTER, getCmsObject().readResource(
                    CmsSitemapServer.PATH_DEFAULT_FORMATTER));
            } catch (CmsException e) {
                // should never happen
                LOG.error(e.getLocalizedMessage(), e);
            }
        }
    }

    /**
     * Handles all sitemap requests.<p>
     * 
     * @return the result
     * 
     * @throws JSONException if there is any problem with JSON
     * @throws CmsException if there is a problem with the cms context
     * 
     * @see org.opencms.workplace.A_CmsAjaxServer#executeAction()
     */
    @Override
    public JSONObject executeAction() throws CmsException, JSONException {

        JSONObject result = new JSONObject();

        HttpServletRequest request = getRequest();
        CmsObject cms = getCmsObject();

        if (!checkParameters(
            request,
            result,
            ReqParam.ACTION.getName(),
            ReqParam.LOCALE.getName(),
            ReqParam.SITEMAP.getName())) {
            // every request needs to have at least these parameters 
            return result;
        }
        String actionParam = request.getParameter(ReqParam.ACTION.getName());
        Action action = Action.valueOf(actionParam.toUpperCase());
        String localeParam = request.getParameter(ReqParam.LOCALE.getName());
        cms.getRequestContext().setLocale(CmsLocaleManager.getLocale(localeParam));
        JSONObject data = new JSONObject();
        if (checkParameters(request, null, ReqParam.DATA.getName())) {
            String dataParam = request.getParameter(ReqParam.DATA.getName());
            data = new JSONObject(dataParam);
        }
        String sitemapParam = request.getParameter(ReqParam.SITEMAP.getName());

        if (action.equals(Action.ALL)) {
            // this should also work for historical requests
            CmsResource sitemapRes = CmsHistoryResourceHandler.getResourceWithHistory(cms, sitemapParam);
            // first load, get everything
            CmsXmlSitemap xmlSitemap = CmsXmlSitemapFactory.unmarshal(cms, sitemapRes, request);
            result = getSitemap(sitemapRes, xmlSitemap);

            CmsSitemapManager manager = OpenCms.getSitemapManager();
            List<CmsResource> creatableElements = manager.getCreatableElements(cms, sitemapParam, request);
            JSONArray creatableTypes = new JSONArray();
            JSONArray models = new JSONArray();
            for (CmsResource resource : creatableElements) {
                String typeName = OpenCms.getResourceManager().getResourceType(resource.getTypeId()).getTypeName();
                creatableTypes.put(typeName);
                CmsSiteEntryBean siteEntryData = resourceToSiteEntryBean(cms, resource);
                if (typeName.equals(CmsResourceTypeXmlContainerPage.getStaticTypeName())) {
                    models.put(jsonifyEntry(siteEntryData));
                }
            }
            addContents(models);
            result.put(JsonResponse.TYPES.getName(), creatableTypes);
            result.put(JsonResponse.MODELS.getName(), models);
        } else if (action.equals(Action.GET)) {
            if (checkParameters(data, null, JsonRequest.FAV.getName())) {
                // get the favorite list
                result.put(JsonResponse.FAVORITES.getName(), getFavoriteList());
            } else if (checkParameters(data, result, JsonRequest.REC.getName())) {
                // get recent list
                result.put(JsonResponse.RECENT.getName(), addContents(m_sessionCache.getRecentList()));
            } else {
                return result;
            }
        } else if (action.equals(Action.SET)) {
            if (checkParameters(data, null, JsonRequest.FAV.getName())) {
                // save the favorite list
                JSONArray list = data.optJSONArray(JsonRequest.FAV.getName());
                saveFavoriteList(cms, list);
            } else if (checkParameters(data, result, JsonRequest.REC.getName())) {
                // save the recent list
                JSONArray list = data.optJSONArray(JsonRequest.REC.getName());
                m_sessionCache.setRecentList(list);
            } else {
                return result;
            }
        } else if (action.equals(Action.VALIDATE)) {
            if (!checkParameters(data, result, JsonRequest.NAME.getName())) {
                return result;
            }
            String name = data.optString(JsonRequest.NAME.getName());
            result.put(JsonResponse.NAME.getName(), cms.getRequestContext().getFileTranslator().translateResource(name));
        } else if (action.equals(Action.PROPS)) {
            CmsResource sitemapRes = cms.readResource(sitemapParam);
            result.put(JsonResponse.PROPERTIES.getName(), getPropertyInfo(sitemapRes));
        } else if (action.equals(Action.SAVE)) {
            // save the sitemap
            CmsResource sitemapRes = cms.readResource(sitemapParam);
            saveSitemap(sitemapRes, data);
        } else if (action.equals(Action.STARTEDIT)) {
            // lock the sitemap
            try {
                cms.lockResourceTemporary(sitemapParam);
            } catch (CmsException e) {
                error(result, e.getLocalizedMessage(getWorkplaceLocale()));
            }
        } else if (action.equals(Action.STOPEDIT)) {
            // lock the sitemap
            try {
                cms.unlockResource(sitemapParam);
            } catch (CmsException e) {
                error(result, e.getLocalizedMessage(getWorkplaceLocale()));
            }
        } else if (action.equals(Action.CONTENT)) {
            JSONArray entries = data.getJSONArray(JsonResponse.ENTRIES.getName().toLowerCase());
            addContents(entries);
            result.put(JsonResponse.ENTRIES.getName().toLowerCase(), entries);
        } else if (action.equals(Action.NEW_ENTRY)) {
            CmsSitemapManager manager = OpenCms.getSitemapManager();
            String type = data.getString(JsonRequest.TYPE.getName());
            CmsResource newResource = manager.createNewElement(cms, sitemapParam, request, type);
            CmsProperty titleProp = cms.readPropertyObject(newResource, CmsPropertyDefinition.PROPERTY_TITLE, false);
            String title = titleProp.getValue();
            String name = newResource.getName();
            String extension = "";
            int dotPos = name.lastIndexOf('.');
            if (dotPos != -1) {
                name = name.substring(0, dotPos);
            }

            CmsSiteEntryBean entryBean = new CmsSiteEntryBean(
                new CmsUUID(),
                newResource.getStructureId(),
                extension,
                title,
                new HashMap<String, String>(),
                new ArrayList<CmsSiteEntryBean>());
            JSONObject jsonEntry = jsonifyEntry(entryBean);
            JSONArray entries = new JSONArray();
            entries.put(jsonEntry);
            addContents(entries);
            result.put(JsonResponse.ENTRY.getName(), jsonEntry);
        } else if (action.equals(Action.NEW_SITEMAP)) {
            CmsSitemapManager manager = OpenCms.getSitemapManager();
            CmsResource newSitemapRes = manager.createSitemap(
                cms,
                data.getString(JsonRequest.TITLE.getName()),
                sitemapParam,
                request);
            saveSitemap(newSitemapRes, data);
            String sitemapId = newSitemapRes.getStructureId().toString();
            result.put(JsonResponse.ID.getName(), sitemapId);
        } else {
            error(result, Messages.get().getBundle(getWorkplaceLocale()).key(
                Messages.ERR_JSON_WRONG_PARAMETER_VALUE_2,
                ReqParam.ACTION.getName(),
                actionParam));
        }
        return result;
    }

    /**
     * Returns the content of an entry when rendered with the given formatter.<p> 
     * 
     * @param entry the site entry bean
     * 
     * @return generated html code
     * 
     * @throws CmsException if an cms related error occurs
     * @throws ServletException if a jsp related error occurs
     * @throws IOException if a jsp related error occurs
     */
    public String getEntryContent(CmsSiteEntryBean entry) throws CmsException, ServletException, IOException {

        CmsObject cms = getCmsObject();
        CmsResource elementRes = cms.readResource(entry.getResourceId());
        I_CmsResourceType type = OpenCms.getResourceManager().getResourceType(elementRes);
        // TODO: use a formatter info bean here!
        return type.getFormattedContent(
            cms,
            getRequest(),
            getResponse(),
            I_CmsResourceType.Formatter.SITEMAP,
            Collections.singletonMap(CmsSitemapManager.ATTR_SITEMAP_ENTRY, (Object)entry.cloneWithoutSubEntries()));
    }

    /**
     * Returns the current user's favorites list.<p>
     * 
     * @return the current user's favorites list
     */
    public JSONArray getFavoriteList() {

        CmsObject cms = getCmsObject();
        CmsUser user = cms.getRequestContext().currentUser();
        Object obj = user.getAdditionalInfo(ADDINFO_SITEMAP_FAVORITE_LIST);

        JSONArray jsonFavList = new JSONArray();
        if (obj instanceof String) {
            try {
                jsonFavList = new JSONArray((String)obj);
            } catch (Throwable e) {
                // should never happen, catches JSON parsing
                if (!LOG.isDebugEnabled()) {
                    LOG.warn(e.getLocalizedMessage());
                }
                LOG.debug(e.getLocalizedMessage(), e);
            }
        } else {
            // save to be better next time
            try {
                saveFavoriteList(cms, jsonFavList);
            } catch (CmsException e) {
                // should never happen, error writing user's additional info 
                LOG.error(e.getLocalizedMessage(), e);
            }
        }

        return addContents(jsonFavList);
    }

    /**
     * Returns the property information for the given sitemap as a JSON object.<p>
     * 
     * @param sitemapRes the sitemap resource
     * 
     * @return the property information
     * 
     * @throws CmsException if something goes wrong
     * @throws JSONException if something goes wrong generating the JSON
     */
    public JSONObject getPropertyInfo(CmsResource sitemapRes) throws CmsException, JSONException {

        CmsObject cms = getCmsObject();

        CmsUserSettings settings = new CmsUserSettings(cms.getRequestContext().currentUser());
        CmsMessages messages = CmsXmlContentDefinition.getContentHandlerForResource(cms, sitemapRes).getMessages(
            settings.getLocale());
        JSONObject jsonProperties = new JSONObject();
        Map<String, CmsXmlContentProperty> propertiesConf = OpenCms.getADEManager().getElementPropertyConfiguration(
            cms,
            sitemapRes);
        Iterator<Map.Entry<String, CmsXmlContentProperty>> itProperties = propertiesConf.entrySet().iterator();
        while (itProperties.hasNext()) {
            Map.Entry<String, CmsXmlContentProperty> entry = itProperties.next();
            String propertyName = entry.getKey();
            CmsXmlContentProperty conf = entry.getValue();
            CmsMacroResolver.resolveMacros(conf.getWidgetConfiguration(), cms, Messages.get().getBundle());
            JSONObject jsonProperty = new JSONObject();
            jsonProperty.put(JsonProperty.DEFAULT_VALUE.getName(), conf.getDefault());
            jsonProperty.put(JsonProperty.TYPE.getName(), conf.getPropertyType());
            jsonProperty.put(JsonProperty.WIDGET.getName(), conf.getWidget());
            jsonProperty.put(JsonProperty.WIDGET_CONF.getName(), CmsMacroResolver.resolveMacros(
                conf.getWidgetConfiguration(),
                cms,
                messages));
            jsonProperty.put(JsonProperty.RULE_TYPE.getName(), conf.getRuleType());
            jsonProperty.put(JsonProperty.RULE_REGEX.getName(), conf.getRuleRegex());
            jsonProperty.put(JsonProperty.NICE_NAME.getName(), CmsMacroResolver.resolveMacros(
                conf.getNiceName(),
                cms,
                messages));
            jsonProperty.put(JsonProperty.DESCRIPTION.getName(), CmsMacroResolver.resolveMacros(
                conf.getDescription(),
                cms,
                messages));
            jsonProperty.put(JsonProperty.ERROR.getName(), CmsMacroResolver.resolveMacros(
                conf.getError(),
                cms,
                messages));
            jsonProperties.put(propertyName, jsonProperty);
        }
        return jsonProperties;
    }

    /**
     * Returns the data for the given sitemap.<p>
     * 
     * @param resource the sitemap's resource 
     * @param xmlSitemap the XML sitemap to use
     * 
     * @return the data for the given sitemap
     * 
     * @throws JSONException if something goes wrong with the JSON manipulation
     * @throws CmsException if something goes wrong
     */
    public JSONObject getSitemap(CmsResource resource, CmsXmlSitemap xmlSitemap) throws JSONException, CmsException {

        CmsObject cms = getCmsObject();
        CmsSitemapBean sitemap = xmlSitemap.getSitemap(cms, cms.getRequestContext().getLocale());
        // create empty result object
        JSONObject result = new JSONObject();

        // collect the site map entries
        JSONArray siteEntries = new JSONArray();
        for (CmsSiteEntryBean entry : sitemap.getSiteEntries()) {
            JSONObject siteEntry = jsonifyEntry(entry);
            if (siteEntry != null) {
                siteEntries.put(siteEntry);
            }
        }
        result.put(JsonResponse.SITEMAP.getName(), siteEntries);

        // check if this is a top-level sitemap, by checking that it is not linked from another sitemap
        boolean topLevel = true;
        JSONArray superSitemapsJSON = new JSONArray();
        CmsRelationFilter filter = CmsRelationFilter.SOURCES.filterType(CmsRelationType.XML_WEAK);
        for (CmsRelation relation : cms.getRelationsForResource(resource, filter)) {
            CmsResource source = relation.getSource(cms, CmsResourceFilter.ALL);
            if (CmsResourceTypeXmlSitemap.isSitemap(source)) {
                topLevel = false;
                superSitemapsJSON.put(cms.getSitePath(source));
            }
        }
        result.put(JsonResponse.SUB_SITEMAP.getName(), !topLevel);
        result.put(JsonResponse.SUPER_SITEMAPS.getName(), superSitemapsJSON);

        // collect the properties
        result.put(JsonResponse.PROPERTIES.getName(), getPropertyInfo(resource));

        return result;
    }

    /**
     * Saves the favorite list, user based.<p>
     * 
     * @param cms the cms context
     * @param favoriteList the element id list
     * 
     * @throws CmsException if something goes wrong 
     */
    public void saveFavoriteList(CmsObject cms, JSONArray favoriteList) throws CmsException {

        CmsUser user = cms.getRequestContext().currentUser();
        user.setAdditionalInfo(ADDINFO_SITEMAP_FAVORITE_LIST, favoriteList.toString());
        cms.writeUser(user);
    }

    /**
     * Saves the new state of the sitemap.<p>
     * 
     * @param sitemapRes the sitemap resource to save
     * @param sitemap the sitemap data
     * 
     * @throws CmsException if something goes wrong with the cms context
     * @throws JSONException if something goes wrong with the JSON manipulation
     */
    public void saveSitemap(CmsResource sitemapRes, JSONObject sitemap) throws CmsException, JSONException {

        CmsObject cms = getCmsObject();

        CmsXmlSitemap xmlSitemap = CmsXmlSitemapFactory.unmarshal(cms, cms.readFile(sitemapRes), getRequest());
        xmlSitemap.save(cms, jsonToEntryList(sitemap.getJSONArray(JsonResponse.SITEMAP.getName()), true));
    }

    /**
     * Adds the HTML content to the JSON list of site entries.<p>
     * 
     * @param jsonEntriesList the JSON list of site entries
     * 
     * @return the original JSON list of site entries with HTML contents
     */
    protected JSONArray addContents(JSONArray jsonEntriesList) {

        List<CmsSiteEntryBean> list = jsonToEntryList(jsonEntriesList, false);
        for (int i = 0; i < jsonEntriesList.length(); i++) {
            try {
                JSONObject json = jsonEntriesList.getJSONObject(i);
                json.put(JsonSiteEntry.CONTENT.getName(), getEntryContent(list.get(i)));
                addContents(json.getJSONArray(JsonSiteEntry.SUBENTRIES.getName()));
            } catch (Exception e) {
                // should never happen, catches JSON parsing
                if (!LOG.isDebugEnabled()) {
                    LOG.warn(e.getLocalizedMessage());
                }
                LOG.debug(e.getLocalizedMessage(), e);
            }
        }
        return jsonEntriesList;
    }

    /**
     * Converts a site entry bean into a JSON object.<p>
     * 
     * @param entry the entry to convert
     * 
     * @return the JSON representation, can be <code>null</code> in case of not enough permissions
     * 
     * @throws JSONException if something goes wrong
     */
    protected JSONObject jsonifyEntry(CmsSiteEntryBean entry) throws JSONException {

        JSONObject result = new JSONObject();
        result.put(JsonSiteEntry.ID.getName(), entry.getId().toString());
        result.put(JsonSiteEntry.NAME.getName(), entry.getName());
        result.put(JsonSiteEntry.TITLE.getName(), entry.getTitle());
        result.put(JsonSiteEntry.LINK_ID.getName(), entry.getResourceId().toString());

        // properties
        JSONObject props = new JSONObject();
        for (Map.Entry<String, String> prop : entry.getProperties().entrySet()) {
            props.put(prop.getKey(), prop.getValue());
        }
        result.put(JsonSiteEntry.PROPERTIES.getName(), props);

        // content
        try {
            result.put(JsonSiteEntry.CONTENT.getName(), getEntryContent(entry));
        } catch (CmsVfsResourceNotFoundException e) {
            // most likely the user has no read permission on this entry
            LOG.debug(e.getLocalizedMessage(), e);
            return null;
        } catch (CmsPermissionViolationException e) {
            // most likely the user has no read permission on this entry
            LOG.debug(e.getLocalizedMessage(), e);
            return null;
        } catch (Exception e) {
            // should never happen
            LOG.error(e.getLocalizedMessage(), e);
            result.put(JsonSiteEntry.CONTENT.getName(), e.getLocalizedMessage());
        }

        // subentries
        JSONArray subentries = new JSONArray();
        for (CmsSiteEntryBean subentry : entry.getSubEntries()) {
            JSONObject jsonSubEntry = jsonifyEntry(subentry);
            if (jsonSubEntry != null) {
                subentries.put(jsonSubEntry);
            }
        }
        result.put(JsonSiteEntry.SUBENTRIES.getName(), subentries);

        return result;
    }

    /**
     * Returns a list of sitemap entries from a json array.<p>
     * 
     * @param array the json array
     * @param recursive if recursive
     * 
     * @return a list of sitemap entries
     */
    protected List<CmsSiteEntryBean> jsonToEntryList(JSONArray array, boolean recursive) {

        List<CmsSiteEntryBean> result = new ArrayList<CmsSiteEntryBean>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            CmsUUID id;
            if (json.has(JsonSiteEntry.ID.getName())) {
                try {
                    id = new CmsUUID(json.optString(JsonSiteEntry.ID.getName()));
                } catch (NumberFormatException e) {
                    LOG.error(e.getLocalizedMessage());
                    continue;
                }
            } else {
                // create a new id if missing
                id = new CmsUUID();
            }
            CmsUUID linkId;
            try {
                linkId = new CmsUUID(json.optString(JsonSiteEntry.LINK_ID.getName()));
            } catch (NumberFormatException e) {
                LOG.error(e.getLocalizedMessage());
                continue;
            }
            String name = json.optString(JsonSiteEntry.NAME.getName());
            String title = json.optString(JsonSiteEntry.TITLE.getName());
            Map<String, String> properties = new HashMap<String, String>();
            JSONObject jsonProps = json.optJSONObject(JsonSiteEntry.PROPERTIES.getName());
            Iterator<String> itKeys = jsonProps.keys();
            while (itKeys.hasNext()) {
                String key = itKeys.next();
                String value = jsonProps.optString(key);
                properties.put(key, value);
            }
            JSONArray jsonSub = json.optJSONArray(JsonSiteEntry.SUBENTRIES.getName());
            CmsSiteEntryBean entry = new CmsSiteEntryBean(id, linkId, name, title, properties, recursive
            ? jsonToEntryList(jsonSub, true)
            : Collections.<CmsSiteEntryBean> emptyList());
            result.add(entry);
        }
        return result;
    }

    /**
     * Converts a resource to a sitemap entry bean.<p>
     *
     * @param cms the CmsObject to use for VFS operations 
     * @param resource the resource to convert
     * 
     * @return the sitemap entry bean for the resource
     * 
     * @throws CmsException if something goes wrong
     */
    protected CmsSiteEntryBean resourceToSiteEntryBean(CmsObject cms, CmsResource resource) throws CmsException {

        CmsProperty titleProp = cms.readPropertyObject(resource, CmsPropertyDefinition.PROPERTY_TITLE, false);
        String title = titleProp.getValue();
        String name = resource.getName();

        int dotPos = name.lastIndexOf('.');
        if (dotPos != -1) {
            name = name.substring(0, dotPos);
        }

        CmsSiteEntryBean entryBean = new CmsSiteEntryBean(
            new CmsUUID(),
            resource.getStructureId(),
            name,
            title,
            new HashMap<String, String>(),
            new ArrayList<CmsSiteEntryBean>());

        return entryBean;
    }
}
