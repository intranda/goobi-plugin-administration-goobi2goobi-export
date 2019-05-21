package de.intranda.goobi.plugins;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;

import org.apache.commons.lang.StringUtils;
import org.goobi.beans.Docket;
import org.goobi.beans.Ldap;
import org.goobi.beans.Project;
import org.goobi.beans.ProjectFileGroup;
import org.goobi.beans.Ruleset;
import org.goobi.beans.User;
import org.goobi.beans.Usergroup;
import org.goobi.production.enums.PluginType;
import org.goobi.production.plugin.interfaces.IAdministrationPlugin;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

import de.sub.goobi.config.ConfigurationHelper;
import de.sub.goobi.helper.StorageProvider;
import de.sub.goobi.persistence.managers.DocketManager;
import de.sub.goobi.persistence.managers.LdapManager;
import de.sub.goobi.persistence.managers.ProjectManager;
import de.sub.goobi.persistence.managers.RulesetManager;
import de.sub.goobi.persistence.managers.UserManager;
import de.sub.goobi.persistence.managers.UsergroupManager;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j

public class ExportDatebasePlugin implements IAdministrationPlugin {

    private static Namespace xmlns = Namespace.getNamespace("http://www.goobi.io/logfile");
    private static final SimpleDateFormat dateConverter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

    @Getter
    private String title = "goobi-plugin-administration-database-export";

    @Getter
    @Setter
    private boolean ldapGroups;
    @Getter
    @Setter
    private boolean userGroups;
    @Getter
    @Setter
    private boolean user;
    @Getter
    @Setter
    private boolean includeInactiveUser;
    @Getter
    @Setter
    private boolean createNewPasswords;
    @Getter
    @Setter
    private boolean projectAssignments;
    @Getter
    @Setter
    private boolean usergroupAssignments;
    @Getter
    @Setter
    private boolean projects;
    @Getter
    @Setter
    private boolean rulesets;
    @Getter
    @Setter
    private boolean dockets;

    @Getter
    @Setter
    private boolean includeFiles;

    @Override
    public PluginType getType() {
        return PluginType.Administration;
    }

    @Override
    public String getGui() {
        return "/uii/administration_dataexport.xhtml";
    }

    public void exportSelectedData() {
        Document exportDoc = new Document();
        Element rootElement = new Element("infrastructure", xmlns);
        exportDoc.setRootElement(rootElement);
        if (ldapGroups) {
            Element ldapConfiguration = new Element("ldaps", xmlns);
            rootElement.addContent(ldapConfiguration);
            for (Ldap ldap : LdapManager.getAllLdapsAsList()) {
                Element ldapElement = createLdapGroupElement(ldap);
                ldapConfiguration.addContent(ldapElement);
            }
        }
        if (rulesets) {
            Element rulesetConfiguration = new Element("rulesets", xmlns);
            rootElement.addContent(rulesetConfiguration);
            for (Ruleset ruleset : RulesetManager.getAllRulesets()) {
                Element rulesetElement = new Element("ruleset", xmlns);
                rulesetElement.setAttribute("id", String.valueOf(ruleset.getId()));
                rulesetElement.setAttribute("file", ruleset.getDatei());
                rulesetElement.setAttribute("name", ruleset.getTitel());
                rulesetConfiguration.addContent(rulesetElement);
            }
        }
        if (dockets) {
            Element docketConfiguration = new Element("dockets", xmlns);
            rootElement.addContent(docketConfiguration);
            for (Docket docket : DocketManager.getAllDockets()) {
                Element docketElement = new Element("docket", xmlns);
                docketElement.setAttribute("id", String.valueOf(docket.getId()));
                docketElement.setAttribute("file", docket.getFile());
                docketElement.setAttribute("name", docket.getName());
                docketConfiguration.addContent(docketElement);
            }
        }

        if (projects) {
            Element projectConfiguration = new Element("projects", xmlns);
            rootElement.addContent(projectConfiguration);
            for (Project project : ProjectManager.getAllProjects()) {
                Element projectData = createProjectElement(project);
                projectConfiguration.addContent(projectData);
                //                if (projectAssignments) {
                //                    Element users = new Element("assignedUsers", xmlns);
                //                    projectData.addContent(users);
                //                    for (User user : project.getBenutzer()) {
                //                        Element userElement = new Element("user", xmlns);
                //                        users.addContent(userElement);
                //                        userElement.setAttribute("id", String.valueOf(user.getId()));
                //                        userElement.setAttribute("login", user.getLogin());
                //                        userElement.setAttribute("name", user.getNachVorname());
                //                    }
                //                }
            }
        }
        if (userGroups) {
            Element userGroups = new Element("userGroups", xmlns);
            rootElement.addContent(userGroups);
            for (Usergroup ug : UsergroupManager.getAllUsergroups()) {
                Element userGroup = new Element("usergroup", xmlns);
                userGroup.setAttribute("id", String.valueOf(ug.getId()));
                userGroup.setAttribute("name", ug.getTitel());
                userGroup.setAttribute("accessLevel", ug.getBerechtigungAsString());
                for (String role : ug.getUserRoles()) {
                    Element roleElement = new Element("role", xmlns);
                    roleElement.setText(role);
                    userGroup.addContent(roleElement);
                }
                userGroups.addContent(userGroup);
                if (usergroupAssignments) {
                    Element users = new Element("assignedUsers", xmlns);
                    userGroup.addContent(users);
                    for (User user : ug.getBenutzer()) {
                        Element userElement = new Element("user", xmlns);
                        users.addContent(userElement);
                        userElement.setAttribute("id", String.valueOf(user.getId()));
                        userElement.setAttribute("login", user.getLogin());
                        userElement.setAttribute("name", user.getNachVorname());
                    }

                }
            }
        }

        if (user) {
            Element users = new Element("users", xmlns);
            rootElement.addContent(users);
            for (User user : UserManager.getAllUsers()) {
                if (includeInactiveUser || user.isIstAktiv()) {
                    Element userElement = createUserElement(user);
                    users.addContent(userElement);
                }
            }

        }

        // write created data into temporary file
        Format format = Format.getPrettyFormat();
        format.setEncoding("UTF-8");
        XMLOutputter xmlOut = new XMLOutputter(format);
        Path xmlOutput = null;
        Path zipFile = null;
        Path temporaryFolder = null;
        try {
            temporaryFolder = Files.createTempDirectory("export");
            xmlOutput = Files.createFile(Paths.get(temporaryFolder.toString(), "export.xml"));
            zipFile = Paths.get(temporaryFolder.toString(), "export.zip");
        } catch (IOException e2) {
            log.error(e2);
            return;
        }
        try (OutputStream os = Files.newOutputStream(xmlOutput)) {
            xmlOut.output(exportDoc, os);
        } catch (IOException e) {
            log.error(e);
        }

        // create a zip file
        Map<String, String> env = new HashMap<>();
        env.put("create", String.valueOf(Files.notExists(zipFile)));
        // use a Zip filesystem URI
        URI fileUri = zipFile.toUri(); // here
        try {
            URI zipUri = new URI("jar:" + fileUri.getScheme(), fileUri.getPath(), null);
            try (FileSystem zipfs = FileSystems.newFileSystem(zipUri, env)) {

                // Create internal path in the zipfs
                Path internalTargetPath = zipfs.getPath("export.xml");
                //    Files.createDirectories(internalTargetPath.getParent());
                // copy a file into the zip file
                Files.copy(xmlOutput, internalTargetPath, StandardCopyOption.REPLACE_EXISTING);

                if (includeFiles) {
                    if (rulesets) {
                        // copy ruleset files
                        for (Path ruleset : StorageProvider.getInstance().listFiles(ConfigurationHelper.getInstance().getRulesetFolder())) {
                            Path rulesetTargetPath = zipfs.getPath("rulesets", ruleset.getFileName().toString());
                            Files.createDirectories(rulesetTargetPath.getParent());
                            Files.copy(ruleset, rulesetTargetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                    if (dockets) {
                        // copy docket files
                        for (Docket docket : DocketManager.getAllDockets()) {
                            Path docketPath = Paths.get(ConfigurationHelper.getInstance().getXsltFolder(), docket.getFile());
                            Path docketTargetPath = zipfs.getPath("dockets", docket.getFile());
                            Files.createDirectories(docketTargetPath.getParent());
                            Files.copy(docketPath, docketTargetPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }

        } catch (URISyntaxException | IOException e) {
            log.error(e);
            return;
        }

        // write zip file to output stream

        FacesContext fc = FacesContext.getCurrentInstance();
        ExternalContext ec = fc.getExternalContext();

        ec.responseReset(); // Some JSF component library or some Filter might have set some headers in the buffer beforehand. We want to get rid of them, else it may collide.
        ec.setResponseContentType("application/zip");
        ec.setResponseHeader("Content-Disposition", "attachment; filename=\"export.zip\"");

        try {
            OutputStream output = ec.getResponseOutputStream();
            Files.copy(zipFile, output);
            //            output.close();
        } catch (IOException e1) {
            log.error(e1);
        }

        fc.responseComplete(); // Important! Otherwise JSF will attempt to render the response which obviously will fail since it's already written with a file and closed.

        // cleanup
        if (Files.exists(temporaryFolder)) {
            StorageProvider.getInstance().deleteDir(temporaryFolder);
        }
    }

    private Element createUserElement(User user) {
        Element userElement = new Element("user", xmlns);
        userElement.setAttribute("id", String.valueOf(user.getId()));
        userElement.setAttribute("firstname", user.getVorname() == null ? "" : user.getVorname());
        userElement.setAttribute("lastname", user.getNachname() == null ? "" : user.getNachname());
        userElement.setAttribute("login", user.getLogin() == null ? "" : user.getLogin());
        userElement.setAttribute("ldaplogin", user.getLdaplogin() == null ? "" : user.getLdaplogin());
        userElement.setAttribute("active", String.valueOf(user.isIstAktiv()));
        userElement.setAttribute("visible", user.getIsVisible() == null ? "" : user.getIsVisible());
        userElement.setAttribute("place", user.getStandort() == null ? "" : user.getStandort());
        userElement.setAttribute("tablesize", String.valueOf(user.getTabellengroesse()));
        userElement.setAttribute("sessionlength", String.valueOf(user.getSessiontimeout()));
        userElement.setAttribute("metadatalanguage", user.getMetadatenSprache() == null ? "" : user.getMetadatenSprache());
        userElement.setAttribute("massdownload", String.valueOf(user.isMitMassendownload()));

        userElement.setAttribute("ldapgroup", user.getLdapGruppe() == null ? "" : user.getLdapGruppe().getTitel());

        userElement.setAttribute("css", user.getCss() == null ? "" : user.getCss());
        userElement.setAttribute("email", user.getEmail() == null ? "" : user.getEmail());
        userElement.setAttribute("shortcut", user.getShortcutPrefix() == null ? "" : user.getShortcutPrefix());
        if (!createNewPasswords) {
            userElement.setAttribute("password", user.getEncryptedPassword());
            userElement.setAttribute("salt", String.valueOf(user.getPasswordSalt()));
        } else {
            userElement.setAttribute("password", "");
            userElement.setAttribute("salt", "");
        }
        userElement.setAttribute("displayDeactivatedProjects", String.valueOf(user.isDisplayDeactivatedProjects()));
        userElement.setAttribute("displayFinishedProcesses", String.valueOf(user.isDisplayFinishedProcesses()));
        userElement.setAttribute("displaySelectBoxes", String.valueOf(user.isDisplaySelectBoxes()));
        userElement.setAttribute("displayIdColumn", String.valueOf(user.isDisplayIdColumn()));
        userElement.setAttribute("displayBatchColumn", String.valueOf(user.isDisplayBatchColumn()));
        userElement.setAttribute("displayProcessDateColumn", String.valueOf(user.isDisplayProcessDateColumn()));
        userElement.setAttribute("displayLocksColumn", String.valueOf(user.isDisplayLocksColumn()));
        userElement.setAttribute("displaySwappingColumn", String.valueOf(user.isDisplaySwappingColumn()));
        userElement.setAttribute("displayModulesColumn", String.valueOf(user.isDisplayModulesColumn()));
        userElement.setAttribute("displayMetadataColumn", String.valueOf(user.isDisplayMetadataColumn()));
        userElement.setAttribute("displayThumbColumn", String.valueOf(user.isDisplayThumbColumn()));
        userElement.setAttribute("displayGridView", String.valueOf(user.isDisplayGridView()));

        userElement.setAttribute("displayAutomaticTasks", String.valueOf(user.isDisplayAutomaticTasks()));
        userElement.setAttribute("hideCorrectionTasks", String.valueOf(user.isHideCorrectionTasks()));
        userElement.setAttribute("displayOnlySelectedTasks", String.valueOf(user.isDisplayOnlySelectedTasks()));
        userElement.setAttribute("displayOnlyOpenTasks", String.valueOf(user.isDisplayOnlyOpenTasks()));
        userElement.setAttribute("displayOtherTasks", String.valueOf(user.isDisplayOtherTasks()));

        userElement.setAttribute("metsDisplayTitle", String.valueOf(user.isMetsDisplayTitle()));
        userElement.setAttribute("metsLinkImage", String.valueOf(user.isMetsLinkImage()));
        userElement.setAttribute("metsDisplayPageAssignments", String.valueOf(user.isMetsDisplayPageAssignments()));
        userElement.setAttribute("metsDisplayHierarchy", String.valueOf(user.isMetsDisplayHierarchy()));
        userElement.setAttribute("metsDisplayProcessID", String.valueOf(user.isMetsDisplayProcessID()));

        userElement.setAttribute("metsEditorTime", String.valueOf(user.getMetsEditorTime()));

        userElement.setAttribute("customColumns", user.getCustomColumns() == null ? "" : user.getCustomColumns());
        userElement.setAttribute("customCss", user.getCustomCss() == null ? "" : user.getCustomCss());

        if (projectAssignments) {
            Element assignedProjects = new Element("assignedProjects", xmlns);
            userElement.addContent(assignedProjects);
            for (Project project :  user.getProjekte()) {
                Element projectElement = new Element("project", xmlns);
                assignedProjects.addContent(projectElement);
                projectElement.setAttribute("id", String.valueOf(project.getId()));
                projectElement.setAttribute("title", project.getTitel());
            }
        }

        return userElement;
    }

    private Element createLdapGroupElement(Ldap ldap) {
        Element ldapElement = new Element("ldap", xmlns);

        ldapElement.setAttribute("id", String.valueOf(ldap.getId()));
        ldapElement.setAttribute("title", ldap.getTitel() == null ? "" : ldap.getTitel());
        ldapElement.setAttribute("homeDirectory", ldap.getHomeDirectory() == null ? "" : ldap.getHomeDirectory());
        ldapElement.setAttribute("gidNumber", ldap.getGidNumber() == null ? "" : ldap.getGidNumber());
        ldapElement.setAttribute("dn", ldap.getUserDN() == null ? "" : ldap.getUserDN());
        ldapElement.setAttribute("objectClass", ldap.getObjectClasses() == null ? "" : ldap.getObjectClasses());
        ldapElement.setAttribute("sambaSID", ldap.getSambaSID() == null ? "" : ldap.getSambaSID());
        ldapElement.setAttribute("sn", ldap.getSn() == null ? "" : ldap.getSn());
        ldapElement.setAttribute("uid", ldap.getUid() == null ? "" : ldap.getUid());
        ldapElement.setAttribute("description", ldap.getDescription() == null ? "" : ldap.getDescription());
        ldapElement.setAttribute("displayName", ldap.getDisplayName() == null ? "" : ldap.getDisplayName());
        ldapElement.setAttribute("gecos", ldap.getGecos() == null ? "" : ldap.getGecos());
        ldapElement.setAttribute("loginShell", ldap.getLoginShell() == null ? "" : ldap.getLoginShell());
        ldapElement.setAttribute("sambaAcctFlags", ldap.getSambaAcctFlags() == null ? "" : ldap.getSambaAcctFlags());
        ldapElement.setAttribute("sambaLogonScript", ldap.getSambaLogonScript() == null ? "" : ldap.getSambaLogonScript());
        ldapElement.setAttribute("sambaPrimaryGroupSID", ldap.getSambaPrimaryGroupSID() == null ? "" : ldap.getSambaPrimaryGroupSID());
        ldapElement.setAttribute("sambaPwdMustChange", ldap.getSambaPwdMustChange() == null ? "" : ldap.getSambaPwdMustChange());
        ldapElement.setAttribute("sambaPasswordHistory", ldap.getSambaPasswordHistory() == null ? "" : ldap.getSambaPasswordHistory());
        ldapElement.setAttribute("sambaLogonHours", ldap.getSambaLogonHours() == null ? "" : ldap.getSambaLogonHours());
        ldapElement.setAttribute("sambaKickoffTime", ldap.getSambaKickoffTime() == null ? "" : ldap.getSambaKickoffTime());
        return ldapElement;
    }

    private Element createProjectElement(Project project) {
        Element projectElement = new Element("project", xmlns);

        // projekte.ProjekteID
        Element projectId = new Element("id", xmlns);
        projectId.setText(String.valueOf(project.getId()));
        projectElement.addContent(projectId);

        // projekte.Titel
        Element projectTitle = new Element("title", xmlns);
        projectTitle.setText(project.getTitel());
        projectElement.addContent(projectTitle);

        // projekte.fileFormatInternal
        Element fileFormatInternal = new Element("fileFormatInternal", xmlns);
        fileFormatInternal.setText(project.getFileFormatInternal());
        projectElement.addContent(fileFormatInternal);

        // projekte.fileFormatDmsExport
        Element fileFormatDmsExport = new Element("fileFormatDmsExport", xmlns);
        fileFormatDmsExport.setText(project.getFileFormatDmsExport());
        projectElement.addContent(fileFormatDmsExport);

        // projekte.startDate
        Element projectStartDate = new Element("startDate", xmlns);
        projectStartDate.setText(dateConverter.format(project.getStartDate()));
        projectElement.addContent(projectStartDate);

        // projekte.endDate
        Element projectEndDate = new Element("endDate", xmlns);
        projectEndDate.setText(dateConverter.format(project.getEndDate()));
        projectElement.addContent(projectEndDate);

        //  projekte.numberOfPages
        Element projectNumberOfPages = new Element("pages", xmlns);
        projectNumberOfPages.setText(String.valueOf(project.getNumberOfPages()));
        projectElement.addContent(projectNumberOfPages);

        // projekte.numberOfPages
        Element projectNumberOfVolumes = new Element("volumes", xmlns);
        projectNumberOfVolumes.setText(String.valueOf(project.getNumberOfVolumes()));
        projectElement.addContent(projectNumberOfVolumes);

        // projekte.projectIsArchived
        projectElement.setAttribute("archived", String.valueOf(project.getProjectIsArchived()));

        // export configuration
        Element exportConfiguration = new Element("exportConfiguration", xmlns);

        // column projekte.useDmsImport
        exportConfiguration.setAttribute("useDmsImport", String.valueOf(project.isUseDmsImport()));

        // projekte.dmsImportTimeOut
        Element dmsImportTimeOut = new Element("dmsImportTimeOut", xmlns);
        dmsImportTimeOut.setText(String.valueOf(project.getDmsImportTimeOut()));
        exportConfiguration.addContent(dmsImportTimeOut);

        // projekte.dmsImportRootPath
        Element dmsImportRootPath = new Element("dmsImportRootPath", xmlns);
        dmsImportRootPath.setText(StringUtils.isBlank(project.getDmsImportRootPath()) ? "" : project.getDmsImportRootPath());
        exportConfiguration.addContent(dmsImportRootPath);

        // projekte.dmsImportImagesPath
        Element dmsImportImagesPath = new Element("dmsImportImagesPath", xmlns);
        dmsImportImagesPath.setText(StringUtils.isBlank(project.getDmsImportImagesPath()) ? "" : project.getDmsImportImagesPath());
        exportConfiguration.addContent(dmsImportImagesPath);

        // projekte.dmsImportSuccessPath
        Element dmsImportSuccessPath = new Element("dmsImportSuccessPath", xmlns);
        dmsImportSuccessPath.setText(StringUtils.isBlank(project.getDmsImportSuccessPath()) ? "" : project.getDmsImportSuccessPath());
        exportConfiguration.addContent(dmsImportSuccessPath);

        // projekte.dmsImportErrorPath
        Element dmsImportErrorPath = new Element("dmsImportErrorPath", xmlns);
        dmsImportErrorPath.setText(StringUtils.isBlank(project.getDmsImportErrorPath()) ? "" : project.getDmsImportErrorPath());
        exportConfiguration.addContent(dmsImportErrorPath);

        // projekte.dmsImportCreateProcessFolder
        exportConfiguration.setAttribute("dmsImportCreateProcessFolder", String.valueOf(project.isDmsImportCreateProcessFolder()));

        projectElement.addContent(exportConfiguration);

        // mets configuration
        Element metsConfiguration = new Element("metsConfiguration", xmlns);

        // projekte.metsRightsOwner
        Element metsRightsOwner = new Element("metsRightsOwner", xmlns);
        metsRightsOwner.setText(StringUtils.isBlank(project.getMetsRightsOwner()) ? "" : project.getMetsRightsOwner());
        metsConfiguration.addContent(metsRightsOwner);

        // projekte.metsRightsOwnerLogo
        Element metsRightsOwnerLogo = new Element("metsRightsOwnerLogo", xmlns);
        metsRightsOwnerLogo.setText(StringUtils.isBlank(project.getMetsRightsOwnerLogo()) ? "" : project.getMetsRightsOwnerLogo());
        metsConfiguration.addContent(metsRightsOwnerLogo);

        // projekte.metsRightsOwnerSite
        Element metsRightsOwnerSite = new Element("metsRightsOwnerSite", xmlns);
        metsRightsOwnerSite.setText(StringUtils.isBlank(project.getMetsRightsOwnerSite()) ? "" : project.getMetsRightsOwnerSite());
        metsConfiguration.addContent(metsRightsOwnerSite);

        // projekte.metsRightsOwnerMail
        Element metsRightsOwnerMail = new Element("metsRightsOwnerMail", xmlns);
        metsRightsOwnerMail.setText(StringUtils.isBlank(project.getMetsRightsOwnerMail()) ? "" : project.getMetsRightsOwnerMail());
        metsConfiguration.addContent(metsRightsOwnerMail);

        // projekte.metsDigiprovReference
        Element metsDigiprovReference = new Element("metsDigiprovReference", xmlns);
        metsDigiprovReference.setText(StringUtils.isBlank(project.getMetsDigiprovReference()) ? "" : project.getMetsDigiprovReference());
        metsConfiguration.addContent(metsDigiprovReference);

        // projekte.metsDigiprovPresentation
        Element metsDigiprovPresentation = new Element("metsDigiprovPresentation", xmlns);
        metsDigiprovPresentation.setText(StringUtils.isBlank(project.getMetsDigiprovPresentation()) ? "" : project.getMetsDigiprovPresentation());
        metsConfiguration.addContent(metsDigiprovPresentation);

        // projekte.metsDigiprovReferenceAnchor
        Element metsDigiprovReferenceAnchor = new Element("metsDigiprovReferenceAnchor", xmlns);
        metsDigiprovReferenceAnchor.setText(StringUtils.isBlank(project.getMetsDigiprovReferenceAnchor()) ? "" : project
                .getMetsDigiprovReferenceAnchor());
        metsConfiguration.addContent(metsDigiprovReferenceAnchor);

        // projekte.metsDigiprovPresentationAnchor
        Element metsDigiprovPresentationAnchor = new Element("metsDigiprovPresentationAnchor", xmlns);
        metsDigiprovPresentationAnchor.setText(StringUtils.isBlank(project.getMetsDigiprovPresentationAnchor()) ? "" : project
                .getMetsDigiprovPresentationAnchor());
        metsConfiguration.addContent(metsDigiprovPresentationAnchor);

        // projekte.metsPointerPath
        Element metsPointerPath = new Element("metsPointerPath", xmlns);
        metsPointerPath.setText(StringUtils.isBlank(project.getMetsPointerPath()) ? "" : project.getMetsPointerPath());
        metsConfiguration.addContent(metsPointerPath);

        // projekte.metsPointerPathAnchor
        Element metsPointerPathAnchor = new Element("metsPointerPathAnchor", xmlns);
        metsPointerPathAnchor.setText(StringUtils.isBlank(project.getMetsPointerPathAnchor()) ? "" : project.getMetsPointerPathAnchor());
        metsConfiguration.addContent(metsPointerPathAnchor);

        // projekte.metsPurl
        Element metsPurl = new Element("metsPurl", xmlns);
        metsPurl.setText(StringUtils.isBlank(project.getMetsPurl()) ? "" : project.getMetsPurl());
        metsConfiguration.addContent(metsPurl);

        // projekte.metsContentIDs
        Element metsContentIDs = new Element("metsContentIDs", xmlns);
        metsContentIDs.setText(StringUtils.isBlank(project.getMetsContentIDs()) ? "" : project.getMetsContentIDs());
        metsConfiguration.addContent(metsContentIDs);

        // projekte.metsRightsSponsor
        Element metsRightsSponsor = new Element("metsRightsSponsor", xmlns);
        metsRightsSponsor.setText(StringUtils.isBlank(project.getMetsRightsSponsor()) ? "" : project.getMetsRightsSponsor());
        metsConfiguration.addContent(metsRightsSponsor);

        // projekte.metsRightsSponsorLogo
        Element metsRightsSponsorLogo = new Element("metsRightsSponsorLogo", xmlns);
        metsRightsSponsorLogo.setText(StringUtils.isBlank(project.getMetsRightsSponsorLogo()) ? "" : project.getMetsRightsSponsorLogo());
        metsConfiguration.addContent(metsRightsSponsorLogo);

        // projekte.metsRightsSponsorSiteURL
        Element metsRightsSponsorSiteURL = new Element("metsRightsSponsorSiteURL", xmlns);
        metsRightsSponsorSiteURL.setText(StringUtils.isBlank(project.getMetsRightsSponsorSiteURL()) ? "" : project.getMetsRightsSponsorSiteURL());
        metsConfiguration.addContent(metsRightsSponsorSiteURL);

        // projekte.metsRightsLicense
        Element metsRightsLicense = new Element("metsRightsLicense", xmlns);
        metsRightsLicense.setText(StringUtils.isBlank(project.getMetsRightsLicense()) ? "" : project.getMetsRightsLicense());
        metsConfiguration.addContent(metsRightsLicense);
        projectElement.addContent(metsConfiguration);

        //   filegroups

        if (!project.getFilegroups().isEmpty()) {
            Element fileGroups = new Element("fileGroups", xmlns);
            projectElement.addContent(fileGroups);
            for (ProjectFileGroup filegroup : project.getFilegroups()) {
                Element projectFileGroup = new Element("projectFileGroup", xmlns);
                // projectfilegroups.ProjectFileGroupID
                projectFileGroup.setAttribute("id", String.valueOf(filegroup.getId()));
                // projectfilegroups.folder
                projectFileGroup.setAttribute("folder", StringUtils.isBlank(filegroup.getFolder()) ? "" : filegroup.getFolder());
                // projectfilegroups.mimetype
                projectFileGroup.setAttribute("mimetype", StringUtils.isBlank(filegroup.getMimetype()) ? "" : filegroup.getMimetype());
                // projectfilegroups.name
                projectFileGroup.setAttribute("name", StringUtils.isBlank(filegroup.getName()) ? "" : filegroup.getName());
                // projectfilegroups.path
                projectFileGroup.setAttribute("path", StringUtils.isBlank(filegroup.getPath()) ? "" : filegroup.getPath());
                // projectfilegroups.suffix
                projectFileGroup.setAttribute("suffix", StringUtils.isBlank(filegroup.getSuffix()) ? "" : filegroup.getSuffix());

                fileGroups.addContent(projectFileGroup);
            }
        }

        return projectElement;
    }
}
