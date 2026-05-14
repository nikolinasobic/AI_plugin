# Intelij AI Plugin

## Overview

This project represents an AI-based plugin for IntelliJ. Groq was used. The plugin supports three features:

1. Code explanation
2. Optimization suggestions
3. README generation

## Setup and Build Instructions

There are two ways to use this plugin.

1. You can download a prebuilt release of the plugin from [this link](https://github.com/nikolinasobic/AI_plugin/releases/tag/v1.0.0)
    1. Navigate to Settings → Plugins → ⚙️ (gear icon) → Install Plugin from Disk - select downloaded .zip file
    2. Set the API key (A more detailed explanation of how to set up the API key can be found below) 
    3. Plugin is ready to use!

2. If you don’t want to install the plugin, follow the steps below.


### Clone repository
You can clone the project using either SSH or HTTPS:
#### ssh
```bash
git clone git@github.com:nikolinasobic/AI_plugin.git
```

#### https
```bash
git clone https://github.com/nikolinasobic/AI_plugin.git
```
##### After cloning
```bash
cd AI_plugin
```

### Build and Run

```bash
./gradlew build
./gradlew run
```

### API key configuration

At this point, you can open a project in which you want to use the plugin. 
The first step is to configure the API key. 
Navigate to Settings → Tools → AI Plugin. 
You can generate your key at [Groq Console](https://console.groq.com/authenticate?stytch_redirect_type=login).


![](images/api_key.png)


After that, the plugin is ready to use!

The “Generate README” feature can be found under Tools → Generate README, while “Explain Code” and “Suggest Optimizations” become available after right-clicking on the selected text. “Suggest Optimizations” and “Explain Code” are grouped under AI Assistant.



## Examples

 
![Explain code](images/explanation.png)

 
![Suggest optimization](images/suggest.png)

 
![Generate README](images/readme.png)

