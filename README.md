<img align="left" src="https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/images/315px-Meister_der_Weltenchronik_001.jpg?raw=true" width="180">

![Build](https://github.com/embabel/grouper/actions/workflows/maven.yml/badge.svg)

![Java](https://img.shields.io/badge/java-%23ED8B00.svg?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring](https://img.shields.io/badge/spring-%236DB33F.svg?style=for-the-badge&logo=spring&logoColor=white)
![Apache Tomcat](https://img.shields.io/badge/apache%20tomcat-%23F8DC75.svg?style=for-the-badge&logo=apache-tomcat&logoColor=black)
![Apache Maven](https://img.shields.io/badge/Apache%20Maven-C71A36?style=for-the-badge&logo=Apache%20Maven&logoColor=white)
![ChatGPT](https://img.shields.io/badge/chatGPT-74aa9c?style=for-the-badge&logo=openai&logoColor=white)
![JSON](https://img.shields.io/badge/JSON-000?logo=json&logoColor=fff)
![GitHub Actions](https://img.shields.io/badge/github%20actions-%232671E5.svg?style=for-the-badge&logo=githubactions&logoColor=white)
![Docker](https://img.shields.io/badge/docker-%230db7ed.svg?style=for-the-badge&logo=docker&logoColor=white)
![IntelliJ IDEA](https://img.shields.io/badge/IntelliJIDEA-000000.svg?style=for-the-badge&logo=intellij-idea&logoColor=white)

&nbsp;&nbsp;&nbsp;&nbsp;

&nbsp;&nbsp;&nbsp;&nbsp;

# AI Focus Groups

Given an objective, tests message wordings to achieve it.
Evolves messaging based on feedback.
Edit [application.yml](src/main/resources/application.yml) to change `maxIterations` and other
config to determine how it behaves.

> Set `maxIterations` to 1 to evaluate the messages passed in, rather than attempt to evolve them.
> This will ensure negligible cost.

# Running

> Running this will cost money. Out of the box expect ~ 10c per run. If you enlarge the participant matrix or evaluate
> longer deliverables this will increase.

Set your OpenAI and Anthropic API keys.
The environment variables are:

- `OPENAI_API_KEY`
- `ANTHROPIC_API_KEY`

> To use local models or other providers, change `application.yml` to
> reference different models.

Run the shell script to start Embabel under Spring Shell:

```bash
./scripts/shell.sh
```

Then run the following command:

```
focus-group --message smoking --group english_teen
```

Messaging files are loaded under `src/data/messages`. The default is `smoking.yml`.
Participant files are loaded under `src/data/participants`. The `english_teen.yml` file shows the format. Use your own
base name.

These files show the required format.

See [application.yml](src/main/resources/application.yml) for other configuration.
This file also controls the creative personas that will attempt to evolve
the messaging.

Also in `application.yml`, you should probably raise `maxIterations` to at least 10
to give the agent the chance to optimize the messaging.

