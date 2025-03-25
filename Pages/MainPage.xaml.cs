using PDFViewer.Models;
using PDFViewer.PageModels;

namespace PDFViewer.Pages;

public partial class MainPage : ContentPage
{
	public MainPage(MainPageModel model)
	{
		InitializeComponent();
		BindingContext = model;
	}
}