# Banner implementation plan

- Place the banner immediately above the bottom navigation.
- Use an adaptive banner format.
- Use Google's test ad unit during development.
- Add UMP consent handling before requesting ads for production where applicable.
- Supply production AdMob identifiers outside source control.
- Premium builds omit the banner and ad SDK initialization.
