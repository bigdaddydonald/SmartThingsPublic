/**
 *  Copyright 2015 SmartThings
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Z-Wave Door/Window Sensor
 *
 *  Author: SmartThings
 *  Date: 2013-11-3
 */

metadata {
	definition(name: "Aeotec Recessed Door Sensor Gen5/7", namespace: "smartthings", author: "SmartThings", ocfDeviceType: "x.com.st.d.sensor.contact", runLocally: false, minHubCoreVersion: '000.017.0012', executeCommandsLocally: false, genericHandler: "Z-Wave") {
		capability "Contact Sensor"
		capability "Sensor"
		capability "Battery"
		capability "Configuration"
		capability "Health Check"

		fingerprint mfr: "0086", prod: "0102", model: "0059", deviceJoinName: "Aeotec Recessed Door Sensor"
		fingerprint mfr: "0086", prod: "0002", model: "0059", deviceJoinName: "Aeotec Recessed Door Sensor"
		fingerprint mfr: "0086", prod: "0002", model: "0078", deviceJoinName: "Aeotec Door/Window Sensor Gen5" //EU
		fingerprint mfr: "0371", prod: "0102", model: "0007", deviceJoinName: "Aeotec Door/Window Sensor 7" //EU
		fingerprint mfr: "0371", prod: "0002", model: "0007", deviceJoinName: "Aeotec Door/Window Sensor 7" //US          
		fingerprint mfr: "0371", prod: "0102", model: "00BB", deviceJoinName: "Aeotec Recessed Door Sensor 7" //US
		fingerprint mfr: "0371", prod: "0002", model: "00BB", deviceJoinName: "Aeotec Recessed Door Sensor 7" //EU
	}

	// simulator metadata
	simulator {
		// status messages
		status "open": "command: 2001, payload: FF"
		status "closed": "command: 2001, payload: 00"
		status "wake up": "command: 8407, payload: "
	}

	// UI tile definitions
	tiles(scale: 2) {
		multiAttributeTile(name: "contact", type: "generic", width: 6, height: 4) {
			tileAttribute("device.contact", key: "PRIMARY_CONTROL") {
				attributeState("open", label: '${name}', icon: "st.contact.contact.open", backgroundColor: "#e86d13")
				attributeState("closed", label: '${name}', icon: "st.contact.contact.closed", backgroundColor: "#00A0DC")
			}
		}
		valueTile("battery", "device.battery", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "battery", label: '${currentValue}% battery', unit: ""
		}

		main "contact"
		details(["contact", "battery"])
	}
}

private getCommandClassVersions() {
	[0x20: 1, 0x25: 1, 0x30: 1, 0x31: 5, 0x80: 1, 0x84: 1, 0x71: 3, 0x9C: 1]
}

def parse(String description) {
	log.debug ""+description
	def result = null
	if (description.startsWith("Err 106")) {
		if (zwaveInfo?.zw?.endsWith("s")) {
			log.debug description
		} 
	} else if (description != "updated") {
		def cmd = zwave.parse(description, commandClassVersions)
		if (cmd) {
			result = zwaveEvent(cmd)
		}
	}
	log.debug "parsed '$description' to $result"
	return result
}

def installed() {
	log.debug "installed()"
	// Device-Watch simply pings if no device events received for 482min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 4 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
	// this is the nuclear option because the device often goes to sleep before we can poll it
	sendEvent(name: "contact", value: "open", descriptionText: "$device.displayName is open")
	sendEvent(name: "battery", unit: "%", value: 100)
}

def updated() {
	log.debug "updated()"
	// Device-Watch simply pings if no device events received for 482min(checkInterval)
	sendEvent(name: "checkInterval", value: 2 * 4 * 60 * 60 + 2 * 60, displayed: false, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID])
}

def configure() {
	log.debug "configure()"
	commands([
        zwave.configurationV1.configurationSet(parameterNumber: 4, size: 2, scaledConfigurationValue: 0),
        zwave.configurationV1.configurationSet(parameterNumber: 5, size: 1, scaledConfigurationValue: 1),
        zwave.configurationV1.configurationGet(parameterNumber: 4),
        zwave.configurationV1.configurationGet(parameterNumber: 5)
    ])
}

def sensorValueEvent(value) {
	if (value) {
		createEvent(name: "contact", value: "open", descriptionText: "$device.displayName is open")
	} else {
		createEvent(name: "contact", value: "closed", descriptionText: "$device.displayName is closed")
	}
}

def zwaveEvent(physicalgraph.zwave.commands.notificationv3.NotificationReport cmd) {
	log.debug ""+cmd
	def result = []
	if (cmd.notificationType == 0x06 && cmd.event == 0x16) {
		result << sensorValueEvent(1)
	} else if (cmd.notificationType == 0x06 && cmd.event == 0x17) {
		result << sensorValueEvent(0)
	} 
	result
}

def zwaveEvent(physicalgraph.zwave.commands.wakeupv1.WakeUpNotification cmd) {
	log.debug "Wakeup Report"
	def event = createEvent(descriptionText: "${device.displayName} woke up", isStateChange: false)
	def cmds = []
    
	cmds << zwave.batteryV1.batteryGet()

	def request = []
	if (cmds.size() > 0) {
		request = commands(cmds, 1000)
		request << "delay 2000"
	}
    
	request << zwave.wakeUpV1.wakeUpNoMoreInformation().format()

	[event, response(request)]
}

def zwaveEvent(physicalgraph.zwave.commands.batteryv1.BatteryReport cmd) {
	log.debug "Battery Report"
	def map = [name: "battery", unit: "%"]
	if (cmd.batteryLevel == 0xFF) {
		map.value = 1
		map.descriptionText = "${device.displayName} has a low battery"
		map.isStateChange = true
	} else {
		map.value = cmd.batteryLevel
	}
	state.lastbat = now()
	[createEvent(map)]
}

def zwaveEvent(physicalgraph.zwave.commands.securityv1.SecurityMessageEncapsulation cmd) {
	def encapsulatedCommand = cmd.encapsulatedCommand(commandClassVersions)
	if (encapsulatedCommand) {
		zwaveEvent(encapsulatedCommand)
	}
}

def zwaveEvent(physicalgraph.zwave.Command cmd) {
	createEvent(descriptionText: "$device.displayName: $cmd", displayed: false)
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv2.ConfigurationReport cmd) {
    log.debug "${cmd.configurationValue}"
}


private command(physicalgraph.zwave.Command cmd) {
	if (zwaveInfo?.zw?.endsWith("s")) {
		zwave.securityV1.securityMessageEncapsulation().encapsulate(cmd).format()
	} else {
		cmd.format()
	}
}

private commands(commands, delay = 200) {
	delayBetween(commands.collect { command(it) }, delay)
}