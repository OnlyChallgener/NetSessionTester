using System;
using System.Diagnostics;
using System.IO;
using System.Reflection;
using System.Windows.Forms;
using Microsoft.Win32;

namespace NetSessionTester
{
    static class Program
    {
        [STAThread]
        static void Main()
        {
            string appDir = AppDomain.CurrentDomain.BaseDirectory;
            string jarPath = Path.Combine(appDir, "NetSessionTester.jar");

            // 1. 若本地不存在 NetSessionTester.jar，尝试从当前 EXE 内嵌资源提取 (单文件便携版)
            if (!File.Exists(jarPath))
            {
                var assembly = Assembly.GetExecutingAssembly();
                string resourceName = null;
                foreach (string name in assembly.GetManifestResourceNames())
                {
                    if (name.EndsWith("NetSessionTester.jar", StringComparison.OrdinalIgnoreCase))
                    {
                        resourceName = name;
                        break;
                    }
                }

                if (resourceName != null)
                {
                    try
                    {
                        string cacheDir = Path.Combine(
                            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
                            "NetSessionTester"
                        );
                        if (!Directory.Exists(cacheDir))
                        {
                            Directory.CreateDirectory(cacheDir);
                        }

                        jarPath = Path.Combine(cacheDir, "NetSessionTester.jar");

                        using (Stream resStream = assembly.GetManifestResourceStream(resourceName))
                        {
                            if (resStream != null)
                            {
                                bool needWrite = true;
                                if (File.Exists(jarPath))
                                {
                                    FileInfo fi = new FileInfo(jarPath);
                                    if (fi.Length == resStream.Length)
                                    {
                                        needWrite = false;
                                    }
                                }

                                if (needWrite)
                                {
                                    string tmpJar = jarPath + ".tmp";
                                    using (FileStream fs = new FileStream(tmpJar, FileMode.Create, FileAccess.Write, FileShare.None))
                                    {
                                        byte[] buf = new byte[65536];
                                        int read;
                                        while ((read = resStream.Read(buf, 0, buf.Length)) > 0)
                                        {
                                            fs.Write(buf, 0, read);
                                        }
                                    }
                                    if (File.Exists(jarPath)) File.Delete(jarPath);
                                    File.Move(tmpJar, jarPath);
                                }
                            }
                        }
                    }
                    catch (Exception ex)
                    {
                        MessageBox.Show(
                            "解包内置运行时失败：\n" + ex.Message,
                            "NetSessionTester - 错误",
                            MessageBoxButtons.OK,
                            MessageBoxIcon.Error
                        );
                        return;
                    }
                }
            }

            if (!File.Exists(jarPath))
            {
                MessageBox.Show(
                    "未在当前目录下找到 NetSessionTester 核心运行时。\n请确保程序未被杀毒软件误拦截。",
                    "NetSessionTester - 缺少主程序",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
                return;
            }

            string javaExe = FindJavaExecutable();

            if (string.IsNullOrEmpty(javaExe))
            {
                DialogResult dr = MessageBox.Show(
                    "未检测到 Java 运行环境 (需 Java 17 或更高版本)。\n\n" +
                    "NetSessionTester 为全平台高性能网络测试引擎，依赖 Java 17+ 运行时。\n\n" +
                    "是否立即前往官网 (Adoptium Temurin) 下载安装？",
                    "NetSessionTester - 环境提示",
                    MessageBoxButtons.YesNo,
                    MessageBoxIcon.Information
                );

                if (dr == DialogResult.Yes)
                {
                    try
                    {
                        Process.Start(new ProcessStartInfo("https://adoptium.net/temurin/releases/") { UseShellExecute = true });
                    }
                    catch { }
                }
                return;
            }

            try
            {
                ProcessStartInfo psi = new ProcessStartInfo
                {
                    FileName = javaExe,
                    Arguments = "-jar \"" + jarPath + "\"",
                    WorkingDirectory = appDir,
                    UseShellExecute = false,
                    CreateNoWindow = true
                };
                Process.Start(psi);
            }
            catch (Exception ex)
            {
                MessageBox.Show(
                    "启动 NetSessionTester 失败：\n" + ex.Message,
                    "启动异常",
                    MessageBoxButtons.OK,
                    MessageBoxIcon.Error
                );
            }
        }

        static string FindJavaExecutable()
        {
            // 1. 检查环境变量 JAVA_HOME
            string javaHome = Environment.GetEnvironmentVariable("JAVA_HOME");
            if (!string.IsNullOrEmpty(javaHome))
            {
                string target = Path.Combine(javaHome, "bin", "javaw.exe");
                if (File.Exists(target)) return target;
                target = Path.Combine(javaHome, "bin", "java.exe");
                if (File.Exists(target)) return target;
            }

            // 2. 检查系统 PATH
            string pathEnv = Environment.GetEnvironmentVariable("PATH");
            if (!string.IsNullOrEmpty(pathEnv))
            {
                string[] paths = pathEnv.Split(';');
                foreach (string p in paths)
                {
                    try
                    {
                        string trimmed = p.Trim();
                        if (string.IsNullOrEmpty(trimmed)) continue;
                        string target = Path.Combine(trimmed, "javaw.exe");
                        if (File.Exists(target)) return target;
                    }
                    catch { }
                }
            }

            // 3. 检查常见安装目录 (Program Files)
            string[] searchBases = {
                @"C:\Program Files\Eclipse Adoptium",
                @"C:\Program Files\Java",
                @"C:\Program Files\Microsoft",
                @"C:\Program Files\BellSoft",
                @"C:\Program Files\Amazon Corretto",
                @"C:\Program Files\Zulu"
            };

            foreach (string baseDir in searchBases)
            {
                if (Directory.Exists(baseDir))
                {
                    try
                    {
                        foreach (string sub in Directory.GetDirectories(baseDir))
                        {
                            string target = Path.Combine(sub, "bin", "javaw.exe");
                            if (File.Exists(target)) return target;
                            target = Path.Combine(sub, "bin", "java.exe");
                            if (File.Exists(target)) return target;
                        }
                    }
                    catch { }
                }
            }

            // 4. 注册表回退查询
            string regJava = CheckRegistryForJava();
            if (!string.IsNullOrEmpty(regJava)) return regJava;

            return null;
        }

        static string CheckRegistryForJava()
        {
            string[] regKeys = {
                @"SOFTWARE\JavaSoft\JDK",
                @"SOFTWARE\JavaSoft\Java Development Kit",
                @"SOFTWARE\JavaSoft\JRE",
                @"SOFTWARE\JavaSoft\Java Runtime Environment",
                @"SOFTWARE\Eclipse Adoptium\JDK"
            };

            foreach (string keyPath in regKeys)
            {
                try
                {
                    using (RegistryKey key = Registry.LocalMachine.OpenSubKey(keyPath))
                    {
                        if (key != null)
                        {
                            foreach (string sub in key.GetSubKeyNames())
                            {
                                using (RegistryKey subKey = key.OpenSubKey(sub))
                                {
                                    if (subKey != null)
                                    {
                                        object home = subKey.GetValue("JavaHome");
                                        if (home != null)
                                        {
                                            string target = Path.Combine(home.ToString(), "bin", "javaw.exe");
                                            if (File.Exists(target)) return target;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                catch { }
            }
            return null;
        }
    }
}
