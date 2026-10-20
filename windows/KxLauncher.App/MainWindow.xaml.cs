using KxLauncher.Core;
using Microsoft.UI.Composition.SystemBackdrops;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Microsoft.UI.Xaml.Media;

namespace KxLauncher.App;

public sealed partial class MainWindow : Window
{
    private VersionManifest? _manifest;

    public MainWindow()
    {
        InitializeComponent();

        // Glass backdrop: Mica Alt on Windows 11, Desktop Acrylic as a fallback.
        if (MicaController.IsSupported())
            SystemBackdrop = new MicaBackdrop { Kind = MicaKind.BaseAlt };
        else if (DesktopAcrylicController.IsSupported())
            SystemBackdrop = new DesktopAcrylicBackdrop();

        // Extend the glass into the title bar.
        ExtendsContentIntoTitleBar = true;
        AppWindow.Resize(new Windows.Graphics.SizeInt32(980, 680));

        Activated += async (_, _) =>
        {
            if (_manifest is null) await LoadAsync();
        };
    }

    private async Task LoadAsync()
    {
        try
        {
            using var client = new MojangClient();
            _manifest = await client.GetVersionManifestAsync();
            StatusText.Text = $"Latest release {_manifest.Latest.Release}";
            ApplyFilter();
        }
        catch (Exception ex)
        {
            StatusText.Text = "Could not load versions: " + ex.Message;
        }

        JavaBox.ItemsSource = JavaLocator.FindJavaExecutables();
        if (JavaBox.Items.Count > 0) JavaBox.SelectedIndex = 0;
    }

    private void ApplyFilter()
    {
        if (_manifest is null) return;
        var type = (TypeFilter.SelectedItem as ComboBoxItem)?.Tag as string ?? "release";
        VersionList.ItemsSource = _manifest.Versions.Where(v => v.Type == type).ToList();
    }

    private void TypeFilter_SelectionChanged(object sender, SelectionChangedEventArgs e) => ApplyFilter();

    private void VersionList_SelectionChanged(object sender, SelectionChangedEventArgs e)
    {
        var selected = VersionList.SelectedItem as VersionEntry;
        PlayButton.IsEnabled = selected is not null;
        SelectionText.Text = selected is null ? "Select a version" : $"Minecraft {selected.Id}";
    }

    private async void PlayButton_Click(object sender, RoutedEventArgs e)
    {
        // Launching (download libraries/assets, build the java command line) is the next step.
        var dialog = new ContentDialog
        {
            XamlRoot = Content.XamlRoot,
            Title = "Not implemented yet",
            Content = "Downloading and starting the game is the next milestone.",
            CloseButtonText = "OK"
        };
        await dialog.ShowAsync();
    }
}
