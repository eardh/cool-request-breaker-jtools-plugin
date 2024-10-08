package com.huang;

import com.huang.net.MessageServer;
import com.lhstack.tools.plugins.Helper;
import com.lhstack.tools.plugins.IPlugin;
import com.lhstack.tools.plugins.Logger;
import com.sun.tools.attach.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Author lei.huang
 * @Description TODO
 **/
public class PluginImpl implements IPlugin {

	public static String agentJar = "CoolRequestAgent.jar";

	private AtomicBoolean breaked = new AtomicBoolean(false);

	Map<String, Logger> loggers = new HashMap<>();

	@Override
	public Icon pluginIcon() {
		return Helper.findIcon("/icons/logo-s3.svg", PluginImpl.class);
	}

	@Override
	public Icon pluginTabIcon() {
		return Helper.findIcon("/icons/logo-s1.svg", PluginImpl.class);
	}

	@Override
	public String pluginName() {
		return "cool-request-breaker";
	}

	@Override
	public String pluginDesc() {
		return "破解 cool-request VIP 插件";
	}

	@Override
	public String pluginVersion() {
		return "1.0.0";
	}

	@Override
	public void install() {
		MessageServer.getServer().start();
		copyFile(getClass().getResource("/lib/" + agentJar));
	}

	@Override
	public void unInstall() {
		MessageServer.getServer().stop();
		File tempLibFile = getTempLibFile(agentJar);
		if (!tempLibFile.exists()) {
			return;
		}
		try {tempLibFile.deleteOnExit();} catch (Throwable ignored) {}
	}

	@Override
	public void openProject(String projectHash, Logger logger, Runnable openThisPage) {
		loggers.put(projectHash, logger);
		if (breaked.get()) {
			return;
		}
		MessageServer server = MessageServer.getServer();
		server.setLoggers(loggers);
		server.start();
	}

	@Override
	public JComponent createPanel(String projectHash) {
		JPanel panel = new JPanel(new GridBagLayout());
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.CENTER;
		gbc.fill = GridBagConstraints.NONE;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;

		JButton button = new JButton();
		button.addActionListener(e -> {
			if (!breaked.get()) {
				breakCoolRequest();
			}
		});

		MessageServer.getServer().setCallback(r -> {
			if (Integer.valueOf(1).equals(r)) {
				button.setIcon(Helper.findIcon("/icons/logo-s2-b.svg", PluginImpl.class));
				button.setEnabled(false);
				breaked.set(true);
				log("成功!");
				return true;
			}
			log("请先打开cool-request插件!");
			return false;
		});

		button.setIcon(Helper.findIcon("/icons/logo-s2.svg", PluginImpl.class));
		button.setText("悟空");
		button.setBorder(BorderFactory.createLineBorder(Color.YELLOW));
		button.setFont(new Font(null, Font.BOLD, 16));
		button.setBorder(BorderFactory.createEmptyBorder());
		button.setToolTipText("点击破解 cool-request VIP");
		if (breaked.get()) {
			button.setIcon(Helper.findIcon("/icons/logo-s2-b.svg", PluginImpl.class));
			button.setEnabled(false);
		}

		panel.add(button, gbc);
		return panel;
	}

	private void breakCoolRequest() {
		File file = getTempLibFile(agentJar);

		List<VirtualMachineDescriptor> list = VirtualMachine.list();
		for (VirtualMachineDescriptor descriptor : list) {
			if (!"com.intellij.idea.Main".equals(descriptor.displayName())) {
				continue;
			}
			try {
				VirtualMachine virtualMachine = VirtualMachine.attach(descriptor);
				virtualMachine.loadAgent(file.getAbsolutePath());
				virtualMachine.detach();
			} catch (AttachNotSupportedException | IOException | AgentLoadException |
			         AgentInitializationException ex) {
				throw new RuntimeException(ex);
			}
		}
	}

	private File getTempLibFile(String fileName) {
		return new File(System.getProperty("java.io.tmpdir"), fileName);
	}

	private File copyFile(URL url) {
		File copyFile = getTempLibFile(agentJar);
		if (copyFile.exists() && !copyFile.delete()) {
			return copyFile;
		}
		try (InputStream inputStream = url.openStream();
		     FileOutputStream outputStream = new FileOutputStream(copyFile)) {
			byte[] buffer = new byte[10240];
			int length;
			while ((length = inputStream.read(buffer)) != -1) {
				outputStream.write(buffer, 0, length);
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return copyFile;
	}

	private void log(String msg) {
		if (loggers == null) {
			return;
		}
		for (Map.Entry<String, Logger> entry : loggers.entrySet()) {
			entry.getValue().warn(msg);
		}
	}
}
