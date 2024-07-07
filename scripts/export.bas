Attribute VB_Name = "Export"
Function BaseName(fName As String) As String
  Dim i, pos As Integer
  
  i = 0
  
  Do
    pos = i + 1
    i = InStr(pos, fName, ".")
  Loop While i > 0
  
  If pos > 0 Then
    BaseName = Left(fName, pos - 2)
  Else
    BaseName = fName
  End If
End Function


Public Sub Export(pres As Presentation, width As Integer)
  Dim Name As String
  Dim r As Double
  
  Debug.Print "Starting"
  Name = BaseName(pres.FullName)
  Debug.Print (Name)
  
  r = pres.PageSetup.SlideWidth / pres.PageSetup.SlideHeight
  
  pres.Slides(1).Export Name & "_" & width & ".png", "PNG", width, Int(width / r + 0.5)
End Sub

Public Sub Run()
  Export ActivePresentation, 2400
  Export ActivePresentation, 1200
  Export ActivePresentation, 600
End Sub
