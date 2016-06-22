/*
 * Copyright 2016 Xtecuan ORG
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.xtecuan.utils;

import freemarker.template.TemplateException;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.mail.internet.MimeMessage;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.ui.freemarker.FreeMarkerConfigurationFactory;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

/**
 *
 * @author xtecuan
 */
@SpringBootApplication
public class MailerApplication {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(MailerApplication.class);
    @Value("${mailer.auth.key}")
    private String authKey;
    @Value("${mailer.auth.val}")
    private Boolean auth;
    @Value("${mailer.ttls.key}")
    private String ttlsKey;
    @Value("${mailer.ttls.val}")
    private Boolean ttls;

    @Bean
    public Properties getJavaMailProperties() {
        Properties props = new Properties();
        props.put(authKey, auth);
        props.put(ttlsKey, ttls);
        return props;
    }

    @Bean
    public JavaMailSender getMailSender(@Value("${mailer.host}") String host, @Value("${mailer.port}") Integer port,
            @Value("${mailer.username}") String username, @Value("${mailer.password}") String password) {

        JavaMailSenderImpl mailer = new JavaMailSenderImpl();
        mailer.setHost(host);
        mailer.setPort(port);
        mailer.setUsername(username);
        mailer.setPassword(getEncoderDecoder().base64Decode(password));
        mailer.setJavaMailProperties(getJavaMailProperties());
        return mailer;
    }

    @Bean
    public LocaleResolver getLocaleResolver() {
        SessionLocaleResolver slr = new SessionLocaleResolver();
        slr.setDefaultLocale(Locale.US);
        return slr;
    }

    @Bean
    public ReloadableResourceBundleMessageSource messageSource() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:locale/filemailer");
        messageSource.setCacheSeconds(3600);
        return messageSource;
    }

    @Bean
    public FreeMarkerConfigurer freemarkerConfig() throws IOException, TemplateException {
        FreeMarkerConfigurationFactory factory = new FreeMarkerConfigurationFactory();
        factory.setTemplateLoaderPaths("classpath:templates");
        factory.setDefaultEncoding("UTF-8");
        FreeMarkerConfigurer result = new FreeMarkerConfigurer();
        result.setConfiguration(factory.createConfiguration());
        return result;
    }

    @Bean
    public EncoderDecoderUtil getEncoderDecoder() {
        return new EncoderDecoderUtil();
    }

    @Bean
    public ZipGenerator getZipGenerator(@Value("${mailer.dir}") String dir,
            @Value("${mailer.ext}") String ext,
            @Value("${mailer.tmpdir}") String tmpdir,
            @Value("${mailer.destzipfile}") String destzipfile
    ) {
        return new ZipGenerator(dir, ext, tmpdir, destzipfile);
    }

    static final class EncoderDecoderUtil {

        public final String base64Encode(String blankWord) {
            byte[] authBytes = blankWord.getBytes(StandardCharsets.UTF_8);
            return Base64.getEncoder().encodeToString(authBytes);
        }

        public final String base64Decode(String shadowWord) {
            return new String(Base64.getDecoder().decode(shadowWord));
        }
    }

    static final class ZipGenerator {

        static final int BUFFER = 2048;
        private final String dir;
        private final String ext;
        private final String tmpdir;
        private final String destzipfile;

        public File getDestzipfile() {
            return new File(new File(tmpdir), destzipfile);
        }

        public ZipGenerator(String dir, String ext, String tmpdir, String destzipfile) {
            this.dir = dir;
            this.ext = ext;
            this.tmpdir = tmpdir;
            this.destzipfile = destzipfile;
        }

        public void createZipFile() {
            File dirFile = new File(dir);
            File tmpdirFile = new File(tmpdir);
            if (!tmpdirFile.exists()) {
                if (tmpdirFile.mkdirs()) {
                    logger.info(tmpdirFile.getPath() + " created!!!");
                }
            }
            MyFileFilter filter = new MyFileFilter(ext);
            File destFile = new File(tmpdirFile, destzipfile);
            File[] files = dirFile.listFiles(filter);
            try {
                BufferedInputStream origin = null;
                FileOutputStream dest = new FileOutputStream(destFile);
                ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(dest));
                out.setMethod(ZipOutputStream.DEFLATED);
                byte data[] = new byte[BUFFER];
                for (int i = 0; i < files.length; i++) {
                    logger.info("Adding: " + files[i]);
                    FileInputStream fi = new FileInputStream(files[i]);
                    origin = new BufferedInputStream(fi, BUFFER);
                    ZipEntry entry = new ZipEntry(files[i].getName());
                    out.putNextEntry(entry);
                    int count;
                    while ((count = origin.read(data, 0,
                            BUFFER)) != -1) {
                        out.write(data, 0, count);
                    }
                    origin.close();
                }
                out.close();
            } catch (Exception e) {
                logger.error("Error creating the file: " + destFile.getPath(), e);
            }

        }

    }

    static final class EncDecResponse {

        private String encoded;
        private String decoded;

        public String getEncoded() {
            return encoded;
        }

        public void setEncoded(String encoded) {
            this.encoded = encoded;
        }

        public String getDecoded() {
            return decoded;
        }

        public void setDecoded(String decoded) {
            this.decoded = decoded;
        }

    }

    static final class MyFileFilter implements FileFilter {

        private final String extension;

        public MyFileFilter(String extension) {
            this.extension = extension;
        }

        @Override
        public boolean accept(File pathname) {
            return pathname.getName().endsWith(extension);
        }

    }

    @RestController
    @RequestMapping("/encdec")
    static final class MyEncoder {

        private final EncoderDecoderUtil util;

        @Autowired
        public MyEncoder(EncoderDecoderUtil util) {
            this.util = util;
        }

        @RequestMapping(value = "/encode", method = RequestMethod.GET)
        public EncDecResponse encode(@RequestParam(name = "pass") String pass) {
            EncDecResponse respo = new EncDecResponse();
            respo.setDecoded(pass);
            respo.setEncoded(util.base64Encode(pass));
            return respo;
        }

        @RequestMapping(value = "/decode", method = RequestMethod.GET)
        public EncDecResponse decode(@RequestParam(name = "pass") String pass) {
            EncDecResponse respo = new EncDecResponse();
            respo.setDecoded(util.base64Decode(pass));
            respo.setEncoded(pass);
            return respo;
        }

    }

    @RestController
    @RequestMapping("/mailer")
    static final class MailSender {

        private final JavaMailSender sender;
        private final FreeMarkerConfigurer fm;
        private final ZipGenerator zg;

        @Autowired
        public MailSender(JavaMailSender sender, FreeMarkerConfigurer fm, ZipGenerator zg) {
            this.sender = sender;
            this.fm = fm;
            this.zg = zg;
        }

        @RequestMapping(value = "/send", method = RequestMethod.GET)
        public boolean sendMail(@RequestParam(name = "to") String to) {
            boolean result = false;
            MimeMessage message = sender.createMimeMessage();

            try {
                MimeMessageHelper helper = new MimeMessageHelper(message, true);
                helper.setTo(to);
                Map<String, Object> model = new HashMap<>();
                model.put("to", to);

                helper.setSubject("Envio de Backup");
                zg.createZipFile();
                FileSystemResource res = new FileSystemResource(zg.getDestzipfile());
                //helper.addInline("bkpFile", res);
                helper.addAttachment(res.getFilename(), res);
                model.put("bkpFile", res.getFilename());

                helper.setText(FreeMarkerTemplateUtils.processTemplateIntoString(
                        fm.getConfiguration().getTemplate("filemailer.ftl"), model), true);
                sender.send(message);
                result = true;
            } catch (Exception ex) {
                logger.error("Error sending emailt to : " + to, ex);
            }

            return result;
        }
    }

    public static void main(String[] args) {
        SpringApplication.run(MailerApplication.class, args);
    }
}
