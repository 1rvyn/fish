## Autofish Mod for Minecraft

### Introduction

This project is a fork of an existing autofish mod for Minecraft, substantially modified to bypass common autofish detection mechanisms on servers. Key features include movement patterns to evade detection, a custom interface to track catches, and a random bamboo farming automation.

### Features

- **Adaptive Fishing**: This mod includes adjustments such as moving the player's head occasionally to avoid detection by plugins that add custom fish and have measures to detect stationary AFK fishing.

- **Custom Fish Tracker**: A custom GUI that renders and displays a count of all custom fish caught, using the custom colours of the custom-fish types *most* servers use.

- **Bamboo Farming Automation**: Exploits the mechanics of player-placed bamboo to automate farming. The mod rapidly places and breaks bamboo *(requires an Efficiency V axe and bamboo in the offhand)*. This feature is designed to operate without breaking the axe. 

### Installation

- **Automatic Setup**: The mod should be automatically built via GitHub Actions. If this does not happen, it can be manually built with assistance from GPT-3.5 to guide through the setup process.

### Motivation

I created this mod as a personal challenge and to alleviate boredom. It was fun developing a Minecraft mod, particularly writing the code to move the player head to avoid the anti-autofish. The current implementation relies on chat messages to record the rarity of fish caught, which could break if the server or mod alters the hex color of these messages.

### Disclaimer

The mod is designed for personal use and learning purposes. Use of this mod on servers without explicit permission could violate server rules.

