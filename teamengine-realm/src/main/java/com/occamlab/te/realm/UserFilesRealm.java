package com.occamlab.te.realm;

import java.io.File;
import java.lang.reflect.Constructor;
import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.catalina.Realm;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.realm.RealmBase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * A Realm implementation that reads user information from an XML file located
 * in a per-user subdirectory of the users directory. A sample representation
 * (to be found in users/p.fogg/user.xml) is shown below. <p>
 * <pre>
 * &lt;user>
 *   &lt;name>p.fogg&lt;/name>
 *   &lt;roles>
 *     &lt;name>user&lt;/name>
 *   &lt;/roles>
 *   &lt;password>password&lt;/password>
 *   &lt;email>p.fogg@example.org&lt;/email>
 * &lt;/user>
 * </pre> </p>
 */
public class UserFilesRealm extends RealmBase {

    private static final Logger LOGR = Logger.getLogger(
            UserFilesRealm.class.getName());
    private String Root = null;
    private DocumentBuilder DB = null;
    private HashMap Principals = new HashMap();

    public String getRoot() {
        return Root;
    }

    public void setRoot(String root) {
        Root = root;
    }

    private GenericPrincipal readPrincipal(String username) {
        List roles = new ArrayList();
        File usersdir = new File(Root);
        if (!usersdir.isDirectory()) {
            usersdir = new File(System.getProperty("catalina.base"), Root);
        }
        File userfile = new File(new File(usersdir, username), "user.xml");
        if (!userfile.canRead()) {
            return null;
        }
        Document userInfo = null;
        try {
            if (DB == null) {
                DB = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            }
            userInfo = DB.parse(userfile);
        } catch (Exception e) {
            LOGR.log(Level.WARNING, "Failed to read user info at "
                    + userfile.getAbsolutePath(), e);
        }
        Element userElement = (Element) (userInfo.getElementsByTagName("user").item(0));
        Element passwordElement = (Element) (userElement.getElementsByTagName(
                "password").item(0));
        String password = passwordElement.getTextContent();
        Element rolesElement = (Element) (userElement.getElementsByTagName(
                "roles").item(0));
        NodeList roleElements = rolesElement.getElementsByTagName("name");
        for (int i = 0; i < roleElements.getLength(); i++) {
            String name = ((Element) roleElements.item(i)).getTextContent();
            roles.add(name);
        }
        GenericPrincipal principal = createGenericPrincipal(username, password, roles);
        return principal;
    }

    /**
     * Creates a new GenericPrincipal representing the specified user.
     *
     * @param username The username for this user.
     * @param password The authentication credentials for this user.
     * @param roles The set of roles (specified using String values) associated
     * with this user.
     * @return A GenericPrincipal for use by this Realm implementation.
     */
    GenericPrincipal createGenericPrincipal(String username, String password,
            List<String> roles) {
        Class klass = null;
        try {
            klass = Class.forName("org.apache.catalina.realm.GenericPrincipal");
        } catch (ClassNotFoundException ex) {
            LOGR.log(Level.SEVERE, ex.getMessage());
        }
        Constructor[] ctors = klass.getConstructors();
        Class firstParamType = ctors[0].getParameterTypes()[0];
        GenericPrincipal principal = null;
        try {
            if (Realm.class.isAssignableFrom(firstParamType)) {
                // for Tomcat 6
                Constructor ctor = klass.getConstructor(new Class[]{
                            Realm.class,
                            String.class,
                            String.class,
                            List.class});
                principal = (GenericPrincipal) ctor.newInstance(
                        new Object[]{this, username, password, roles});
            } else {
                // Realm parameter absent in Tomcat 7
                Constructor ctor = klass.getConstructor(new Class[]{
                            String.class,
                            String.class,
                            List.class});
                principal = (GenericPrincipal) ctor.newInstance(
                        new Object[]{username, password, roles});
            }
        } catch (Exception ex) {
            LOGR.log(Level.WARNING, ex.getMessage());
        }
        return principal;
    }

    @Override
    protected String getPassword(String username) {
        GenericPrincipal principal = (GenericPrincipal) getPrincipal(username);
        if (principal == null) {
            return null;
        } else {
            return principal.getPassword();
        }
    }

    @Override
    protected Principal getPrincipal(String username) {
        Principal principal;

        // Reread principal from file system if there is an asterisk (*) before the username
        // This allows you to reset passwords without restarting Tomcat
        // Just reset the password in the user.xml file, and attempt to login using *username
        if (username.startsWith("*")) {
            principal = readPrincipal(username.substring(1));
            if (principal != null) {
                synchronized (Principals) {
                    Principals.put(username.substring(1), principal);
                }
            }
        }

        synchronized (Principals) {
            principal = (Principal) Principals.get(username);
        }
        if (principal == null) {
            principal = readPrincipal(username);
            if (principal != null) {
                synchronized (Principals) {
                    Principals.put(username, principal);
                }
            }
        }
        return principal;
    }

    @Override
    protected String getName() {
        return "UserFilesRealm";
    }
}
