import io.github.foldaspect.unlock.UnlockPolicy;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import java.io.File;

public class PolicyRegression {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        check(UnlockPolicy.supports("lhasa", 37, "OS4.0.21.0.XPNCNXM"), "inspected build must match");
        check(!UnlockPolicy.supports("lhasa", 37, "OS4.0.22.0.XPNCNXM"), "uninspected OTA must stay untouched");
        check(!UnlockPolicy.supports("lhasa", 36, "OS4.0.21.0.XPNCNXM"), "different Android must stay untouched");
        check(!UnlockPolicy.supports("other", 37, "OS4.0.21.0.XPNCNXM"), "different device must stay untouched");
        for (String name : new String[]{"android", "com.android.systemui", "com.miui.home", "com.android.permissioncontroller", "com.google.android.permissioncontroller"}) {
            check(!UnlockPolicy.mayUnlockPackage(name), "protected package " + name);
            check(!UnlockPolicy.mayUnlockApplication(name, 10123), "protected app UID " + name);
            check(!UnlockPolicy.mayUnlockActivity(name + "/.Main"), "protected component " + name);
        }
        for (int uid : new int[]{-1, 0, 1000, 2000, 9999, 99000, 199000, 101000}) {
            check(!UnlockPolicy.mayUnlockApplication("com.miui.notes", uid), "privileged or isolated UID " + uid);
        }
        for (int uid : new int[]{10000, 10345, 110345, 99910345}) {
            check(UnlockPolicy.mayUnlockApplication("com.miui.notes", uid), "ordinary app across Android users " + uid);
        }
        check(!UnlockPolicy.mayUnlockPackage(null), "null package");
        check(!UnlockPolicy.mayUnlockActivity("pkg"), "no component separator");
        check(!UnlockPolicy.mayUnlockActivity("pkg/"), "empty class");
        if (args.length == 0) {
            System.out.println("PASS " + checks + " portable policy checks.");
            System.out.println("Optional: supply a locally obtained firmware policy XML for device-specific rule checks.");
            return;
        }
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc = f.newDocumentBuilder().parse(new File(args[0]));
        int packages = 0, activities = 0;
        NodeList p = doc.getElementsByTagName("package");
        for (int i = 0; i < p.getLength(); i++) {
            Element element = (Element) p.item(i);
            if ("false".equals(element.getAttribute("compat"))) {
                check(UnlockPolicy.mayUnlockPackage(element.getAttribute("name")), "firmware package rule");
                packages++;
            }
        }
        NodeList a = doc.getElementsByTagName("activity");
        for (int i = 0; i < a.getLength(); i++) {
            Element element = (Element) a.item(i);
            if ("false".equals(element.getAttribute("compat"))) {
                check(UnlockPolicy.mayUnlockActivity(element.getAttribute("name")), "firmware activity rule");
                activities++;
            }
        }
        check(packages == 61 && activities == 21, "reference firmware rule counts changed");
        System.out.println("PASS " + checks + " checks; " + packages + " package rules and " + activities + " activity rules covered.");
        System.out.println("Offline policy regression only; does not establish Android hook activation or app rendering.");
    }
}
